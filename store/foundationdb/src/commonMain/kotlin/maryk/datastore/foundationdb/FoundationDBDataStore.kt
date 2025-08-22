package maryk.datastore.foundationdb

import com.apple.foundationdb.FDB
import com.apple.foundationdb.TransactionContext
import com.apple.foundationdb.directory.DirectoryLayer
import com.apple.foundationdb.directory.DirectorySubspace
import com.apple.foundationdb.tuple.Tuple
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.future.await
import kotlinx.coroutines.launch
import maryk.core.clock.HLC
import maryk.core.exceptions.DefNotFoundException
import maryk.core.exceptions.TypeException
import maryk.core.models.IsRootDataModel
import maryk.core.models.migration.MigrationException
import maryk.core.models.migration.MigrationHandler
import maryk.core.models.migration.MigrationStatus
import maryk.core.models.migration.MigrationStatus.NeedsMigration
import maryk.core.models.migration.MigrationStatus.NewIndicesOnExistingProperties
import maryk.core.models.migration.MigrationStatus.NewModel
import maryk.core.models.migration.MigrationStatus.OnlySafeAdds
import maryk.core.models.migration.MigrationStatus.UpToDate
import maryk.core.models.migration.StoredRootDataModelDefinition
import maryk.core.models.migration.VersionUpdateHandler
import maryk.core.properties.definitions.index.IsIndexable
import maryk.core.query.DefinitionsConversionContext
import maryk.core.query.requests.AddRequest
import maryk.core.query.requests.ChangeRequest
import maryk.core.query.requests.DeleteRequest
import maryk.core.query.requests.GetChangesRequest
import maryk.core.query.requests.GetRequest
import maryk.core.query.requests.GetUpdatesRequest
import maryk.core.query.requests.ScanChangesRequest
import maryk.core.query.requests.ScanRequest
import maryk.core.query.requests.ScanUpdatesRequest
import maryk.core.query.responses.AddResponse
import maryk.core.query.responses.UpdateResponse
import maryk.datastore.foundationdb.model.storeModelDefinition
import maryk.datastore.foundationdb.processors.deleteCompleteIndexContents
import maryk.datastore.foundationdb.processors.processAddRequest
import maryk.datastore.foundationdb.processors.walkDataRecordsAndFillIndex
import maryk.datastore.shared.AbstractDataStore
import maryk.datastore.shared.StoreAction

/**
 * FoundationDB DataStore (JVM-only).
 */
class FoundationDBDataStore private constructor(
    override val keepAllVersions: Boolean = true,
    val fdbClusterFilePath: String? = null,
    val tenantName: Tuple? = null,
    val directoryRootPath: List<String> = listOf("maryk"),
    dataModelsById: Map<UInt, IsRootDataModel>,
    private val onlyCheckModelVersion: Boolean = false,
    val migrationHandler: MigrationHandler<FoundationDBDataStore>? = null,
    val versionUpdateHandler: VersionUpdateHandler<FoundationDBDataStore>? = null,
) : AbstractDataStore(dataModelsById, Dispatchers.IO.limitedParallelism(1)) {
    override val supportsFuzzyQualifierFiltering: Boolean = false
    override val supportsSubReferenceFiltering: Boolean = false

    private val fdb = FDB.selectAPIVersion(740)
    private val db = (if (fdbClusterFilePath != null) fdb.open(fdbClusterFilePath) else fdb.open())
    private val tenantDB = tenantName?.let { db.openTenant(tenantName) }
    internal val tc: TransactionContext = tenantDB ?: db

    private val scheduledVersionUpdateHandlers = mutableListOf<suspend () -> Unit>()

    private lateinit var rootDirectory: DirectorySubspace
    internal val directoriesByDataModelIndex = mutableMapOf<UInt, IsTableDirectories>()

    suspend fun initAsync() {
        rootDirectory = tc.runAsync { tr ->
            DirectoryLayer.getDefault().createOrOpen(tr, directoryRootPath)
        }.await()

        if (keepAllVersions) {
            for ((index, dataModel) in dataModelsById) {
                directoriesByDataModelIndex[index] = openHistoricTableDirs(dataModel.Meta.name)
            }
        } else {
            for ((index, dataModel) in dataModelsById) {
                directoriesByDataModelIndex[index] = openNonHistoricTableDirs(dataModel.Meta.name)
            }
        }

        val conversionContext = DefinitionsConversionContext()

        for ((index, dataModel) in dataModelsById) {
            directoriesByDataModelIndex[index]?.let { tableDirectories ->
                when (val migrationStatus = checkModelIfMigrationIsNeeded(tc, tableDirectories.model, dataModel, onlyCheckModelVersion, conversionContext)) {
                    UpToDate, MigrationStatus.AlreadyProcessed -> Unit // Do nothing since no work is needed
                    NewModel -> {
                        scheduledVersionUpdateHandlers.add {
                            versionUpdateHandler?.invoke(this, null, dataModel)
                            storeModelDefinition(tc, tableDirectories.model, dataModel)
                        }
                    }
                    is OnlySafeAdds -> {
                        scheduledVersionUpdateHandlers.add {
                            versionUpdateHandler?.invoke(this, migrationStatus.storedDataModel as StoredRootDataModelDefinition, dataModel)
                            storeModelDefinition(tc, tableDirectories.model, dataModel)
                        }
                    }
                    is NewIndicesOnExistingProperties -> {
                        fillIndex(migrationStatus.indexesToIndex, tableDirectories)
                        scheduledVersionUpdateHandlers.add {
                            versionUpdateHandler?.invoke(this, migrationStatus.storedDataModel as StoredRootDataModelDefinition, dataModel)
                            storeModelDefinition(tc, tableDirectories.model, dataModel)
                        }
                    }
                    is NeedsMigration -> {
                        val succeeded = migrationHandler?.invoke(this, migrationStatus.storedDataModel as StoredRootDataModelDefinition, dataModel)
                            ?: throw MigrationException("Migration needed: No migration handler present. \n$migrationStatus")

                        if (!succeeded) {
                            throw MigrationException("Migration could not be handled for ${dataModel.Meta.name} & ${(migrationStatus.storedDataModel as? StoredRootDataModelDefinition)?.Meta?.version}\n$migrationStatus")
                        }

                        migrationStatus.indexesToIndex?.let {
                            fillIndex(it, tableDirectories)
                        }
                        scheduledVersionUpdateHandlers.add {
                            versionUpdateHandler?.invoke(this, migrationStatus.storedDataModel as StoredRootDataModelDefinition, dataModel)
                            storeModelDefinition(tc, tableDirectories.model, dataModel)
                        }
                    }
                }
            }
        }

        startFlows()

        scheduledVersionUpdateHandlers.forEach { it() }
    }

    private fun openHistoricTableDirs(
        modelName: String
    ): HistoricTableDirectories = tc.run { tr ->
        val modelDir        = rootDirectory.createOrOpen(tr, listOf(modelName))
        val keysDir         = rootDirectory.createOrOpen(tr, listOf(modelName, "keys"))
        val tableDir        = rootDirectory.createOrOpen(tr, listOf(modelName, "table"))
        val uniqueDir       = rootDirectory.createOrOpen(tr, listOf(modelName, "unique"))
        val indexDir        = rootDirectory.createOrOpen(tr, listOf(modelName, "index"))
        val histTableDir    = rootDirectory.createOrOpen(tr, listOf(modelName, "table_versioned"))
        val histIndexDir    = rootDirectory.createOrOpen(tr, listOf(modelName, "index_versioned"))
        val histUniqueDir   = rootDirectory.createOrOpen(tr, listOf(modelName, "unique_versioned"))

        HistoricTableDirectories(
            model         = modelDir.join(),
            keys          = keysDir.join(),
            table         = tableDir.join(),
            unique        = uniqueDir.join(),
            index         = indexDir.join(),
            historicTable = histTableDir.join(),
            historicIndex = histIndexDir.join(),
            historicUnique= histUniqueDir.join()
        )
    }

    private fun openNonHistoricTableDirs(
        modelName: String
    ): TableDirectories = tc.run { tr ->
        val modelDir  = rootDirectory.createOrOpen(tr, listOf(modelName))
        val keysDir   = rootDirectory.createOrOpen(tr, listOf(modelName, "keys"))
        val tableDir  = rootDirectory.createOrOpen(tr, listOf(modelName, "table"))
        val uniqueDir = rootDirectory.createOrOpen(tr, listOf(modelName, "unique"))
        val indexDir  = rootDirectory.createOrOpen(tr, listOf(modelName, "index"))

        TableDirectories(
            model  = modelDir.join(),
            keys   = keysDir.join(),
            table  = tableDir.join(),
            unique = uniqueDir.join(),
            index  = indexDir.join()
        )
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun startFlows() {
        super.startFlows()

        this.launch {
//            val cache = Cache()

            var clock = HLC()
            storeActorHasStarted.complete(Unit)
            try {
                for (storeAction in storeChannel) {
                    try {
                        clock = clock.calculateMaxTimeStamp()
                        @Suppress("UNCHECKED_CAST")
                        when (val request = storeAction.request) {
                            is AddRequest<*> ->
                                processAddRequest(clock, storeAction as StoreAction<IsRootDataModel, AddRequest<IsRootDataModel>, AddResponse<IsRootDataModel>>)
                            is ChangeRequest<*> ->
                                TODO("Change requests are not yet implemented in FoundationDB")
                            is DeleteRequest<*> ->
                                TODO("Delete requests are not yet implemented in FoundationDB")
                            is GetRequest<*> ->
                                TODO("Get requests are not yet implemented in FoundationDB")
                            is ScanRequest<*> ->
                                TODO("Scan requests are not yet implemented in FoundationDB")
                            is GetChangesRequest<*> ->
                                TODO("GetChanges requests are not yet implemented in FoundationDB")
                            is ScanChangesRequest<*> ->
                                TODO("ScanChanges requests are not yet implemented in FoundationDB")
                            is GetUpdatesRequest<*> ->
                                TODO("GetUpdates requests are not yet implemented in FoundationDB")
                            is ScanUpdatesRequest<*> ->
                                TODO("ScanUpdates requests are not yet implemented in FoundationDB")
                            is UpdateResponse<*> ->
                                TODO("Update responses are not yet implemented in FoundationDB")
                            else -> throw TypeException("Unsupported request type ${request::class.simpleName}")
                        }
                    } catch (e: CancellationException) {
                        storeAction.response.cancel(e)
                        throw e
                    } catch (e: Throwable) {
                        storeAction.response.completeExceptionally(e)
                    }
                }
            } finally {
                while (!storeChannel.isEmpty) {
                    val pending = storeChannel.tryReceive().getOrNull() ?: break
                    pending.response.completeExceptionally(CancellationException("Datastore closing"))
                }
            }
        }
    }

    /** Walk all current values in [tableDirectories] and fill [indexesToIndex] */
    private fun fillIndex(
        indexesToIndex: List<IsIndexable>,
        tableDirectories: IsTableDirectories,
    ) {
        for (indexable in indexesToIndex) {
            deleteCompleteIndexContents(tc, tableDirectories, indexable)
        }

        walkDataRecordsAndFillIndex(tc, tableDirectories, indexesToIndex)
    }

    internal fun getTableDirs(dbIndex: UInt) =
        directoriesByDataModelIndex[dbIndex]
            ?: throw DefNotFoundException("DataModel definition not found for $dbIndex")

    override suspend fun close() {
        this.tenantDB?.close()
        this.db.close()
        super.close()
    }

    companion object {
        suspend fun open(
            keepAllVersions: Boolean = true,
            fdbClusterFilePath: String? = null,
            directoryPath: List<String> = listOf("maryk"),
            tenantName: Tuple? = null,
            dataModelsById: Map<UInt, IsRootDataModel>,
            onlyCheckModelVersion: Boolean = false,
            migrationHandler: MigrationHandler<FoundationDBDataStore>? = null,
            versionUpdateHandler: VersionUpdateHandler<FoundationDBDataStore>? = null,
        ) = FoundationDBDataStore(
            keepAllVersions = keepAllVersions,
            fdbClusterFilePath = fdbClusterFilePath,
            directoryRootPath = directoryPath,
            tenantName = tenantName,
            dataModelsById = dataModelsById,
            onlyCheckModelVersion = onlyCheckModelVersion,
            migrationHandler = migrationHandler,
            versionUpdateHandler = versionUpdateHandler
        ).apply {
            try {
                initAsync()
            } catch (e: Throwable) {
                this.close()
                throw e
            }
        }
    }
}
