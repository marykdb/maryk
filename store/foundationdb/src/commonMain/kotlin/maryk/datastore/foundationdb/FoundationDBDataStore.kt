package maryk.datastore.foundationdb

import com.apple.foundationdb.DatabaseOptions
import com.apple.foundationdb.FDB
import com.apple.foundationdb.Transaction
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
import maryk.core.exceptions.RequestException
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
import maryk.core.query.responses.UpdateResponse
import maryk.core.query.responses.updates.AdditionUpdate
import maryk.core.query.responses.updates.ChangeUpdate
import maryk.core.query.responses.updates.InitialChangesUpdate
import maryk.core.query.responses.updates.InitialValuesUpdate
import maryk.core.query.responses.updates.OrderedKeysUpdate
import maryk.core.query.responses.updates.RemovalUpdate
import maryk.datastore.foundationdb.model.checkModelIfMigrationIsNeeded
import maryk.datastore.foundationdb.model.storeModelDefinition
import maryk.datastore.foundationdb.processors.AnyAddStoreAction
import maryk.datastore.foundationdb.processors.AnyChangeStoreAction
import maryk.datastore.foundationdb.processors.AnyDeleteStoreAction
import maryk.datastore.foundationdb.processors.AnyGetChangesStoreAction
import maryk.datastore.foundationdb.processors.AnyGetStoreAction
import maryk.datastore.foundationdb.processors.AnyGetUpdatesStoreAction
import maryk.datastore.foundationdb.processors.AnyProcessUpdateResponseStoreAction
import maryk.datastore.foundationdb.processors.AnyScanChangesStoreAction
import maryk.datastore.foundationdb.processors.AnyScanStoreAction
import maryk.datastore.foundationdb.processors.AnyScanUpdatesStoreAction
import maryk.datastore.foundationdb.processors.deleteCompleteIndexContents
import maryk.datastore.foundationdb.processors.helpers.awaitResult
import maryk.datastore.foundationdb.processors.processAddRequest
import maryk.datastore.foundationdb.processors.processAdditionUpdate
import maryk.datastore.foundationdb.processors.processChangeRequest
import maryk.datastore.foundationdb.processors.processChangeUpdate
import maryk.datastore.foundationdb.processors.processDeleteRequest
import maryk.datastore.foundationdb.processors.processDeleteUpdate
import maryk.datastore.foundationdb.processors.processGetChangesRequest
import maryk.datastore.foundationdb.processors.processGetRequest
import maryk.datastore.foundationdb.processors.processGetUpdatesRequest
import maryk.datastore.foundationdb.processors.processInitialChangesUpdate
import maryk.datastore.foundationdb.processors.processScanChangesRequest
import maryk.datastore.foundationdb.processors.processScanRequest
import maryk.datastore.foundationdb.processors.processScanUpdatesRequest
import maryk.datastore.foundationdb.processors.walkDataRecordsAndFillIndex
import maryk.datastore.shared.AbstractDataStore
import maryk.datastore.shared.Cache
import java.util.concurrent.CompletableFuture
import java.util.function.Function

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
    val databaseOptionsSetter: DatabaseOptions.() -> Unit = {},
    val migrationHandler: MigrationHandler<FoundationDBDataStore>? = null,
    val versionUpdateHandler: VersionUpdateHandler<FoundationDBDataStore>? = null,
) : AbstractDataStore(dataModelsById, Dispatchers.IO.limitedParallelism(1)) {
    override val supportsFuzzyQualifierFiltering: Boolean = true
    override val supportsSubReferenceFiltering: Boolean = true

    private val fdb = FDB.selectAPIVersion(730)
    private val db = (if (fdbClusterFilePath != null) fdb.open(fdbClusterFilePath) else fdb.open())
    private val tenantDB = tenantName?.let { db.openTenant(tenantName) }
    internal val tc: TransactionContext = tenantDB ?: db

    init {
        db.options().apply {
            databaseOptionsSetter()
        }
    }

    // Keep the actor single-threaded for transactional safety while dispatching updates on IO.
    internal val updateDispatcher = Dispatchers.IO

    private val scheduledVersionUpdateHandlers = mutableListOf<suspend () -> Unit>()

    private lateinit var rootDirectory: DirectorySubspace
    internal val directoriesByDataModelIndex = mutableMapOf<UInt, IsTableDirectories>()

    internal inline fun <T> runTransaction(
        crossinline block: (Transaction) -> T
    ): T =
        tc.run(Function { tr ->
            block(tr)
        })

    internal inline fun <T> runTransactionAsync(
        crossinline block: (Transaction) -> CompletableFuture<T>
    ): CompletableFuture<T> =
        tc.runAsync(Function { tr ->
            block(tr)
        })

    suspend fun initAsync() {
        rootDirectory = runTransactionAsync { tr ->
            DirectoryLayer.getDefault().createOrOpen(tr, directoryRootPath)
        }.await()

        for ((index, dataModel) in dataModelsById) {
            directoriesByDataModelIndex[index] = openTableDirs(dataModel.Meta.name, historic = keepAllVersions)
        }

        val conversionContext = DefinitionsConversionContext()

        for ((index, dataModel) in dataModelsById) {
            directoriesByDataModelIndex[index]?.let { tableDirectories ->
                when (val migrationStatus = checkModelIfMigrationIsNeeded(tc, tableDirectories.modelPrefix, dataModel, onlyCheckModelVersion, conversionContext)) {
                    UpToDate, MigrationStatus.AlreadyProcessed -> Unit // Do nothing since no work is needed
                    NewModel -> {
                        // Persist model metadata immediately to ensure subsequent opens see it
                        storeModelDefinition(tc, tableDirectories.modelPrefix, dataModel)
                        scheduledVersionUpdateHandlers.add {
                            versionUpdateHandler?.invoke(this, null, dataModel)
                        }
                    }
                    is OnlySafeAdds -> {
                        // Conservatively (re)index all indexes defined on the new model
                        dataModel.Meta.indexes?.let { idxs ->
                            if (idxs.isNotEmpty()) {
                                fillIndex(idxs, tableDirectories)
                            }
                        }
                        storeModelDefinition(tc, tableDirectories.modelPrefix, dataModel)
                        scheduledVersionUpdateHandlers.add {
                            versionUpdateHandler?.invoke(this, migrationStatus.storedDataModel as StoredRootDataModelDefinition, dataModel)
                        }
                    }
                    is NewIndicesOnExistingProperties -> {
                        fillIndex(migrationStatus.indexesToIndex, tableDirectories)
                        storeModelDefinition(tc, tableDirectories.modelPrefix, dataModel)
                        scheduledVersionUpdateHandlers.add {
                            versionUpdateHandler?.invoke(this, migrationStatus.storedDataModel as StoredRootDataModelDefinition, dataModel)
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
                        storeModelDefinition(tc, tableDirectories.modelPrefix, dataModel)
                        scheduledVersionUpdateHandlers.add {
                            versionUpdateHandler?.invoke(this, migrationStatus.storedDataModel as StoredRootDataModelDefinition, dataModel)
                        }
                    }
                }
            }
        }

        startFlows()

        scheduledVersionUpdateHandlers.forEach { it() }
    }

    private fun openTableDirs(
        modelName: String,
        historic: Boolean = false
    ): IsTableDirectories = runTransaction { tr ->
        val modelDir        = rootDirectory.createOrOpen(tr, listOf(modelName, "meta")).awaitResult()
        val keysDir         = rootDirectory.createOrOpen(tr, listOf(modelName, "keys")).awaitResult()
        val tableDir        = rootDirectory.createOrOpen(tr, listOf(modelName, "table")).awaitResult()
        val uniqueDir       = rootDirectory.createOrOpen(tr, listOf(modelName, "unique")).awaitResult()
        val indexDir        = rootDirectory.createOrOpen(tr, listOf(modelName, "index")).awaitResult()

        if (!historic) {
            TableDirectories(
                dataStore = this,
                model  = modelDir,
                keys   = keysDir,
                table  = tableDir,
                unique = uniqueDir,
                index  = indexDir
            )
        } else {
            val histTableDir    = rootDirectory.createOrOpen(tr, listOf(modelName, "table_versioned")).awaitResult()
            val histIndexDir    = rootDirectory.createOrOpen(tr, listOf(modelName, "index_versioned")).awaitResult()
            val histUniqueDir   = rootDirectory.createOrOpen(tr, listOf(modelName, "unique_versioned")).awaitResult()

            HistoricTableDirectories(
                dataStore     = this,
                model         = modelDir,
                keys          = keysDir,
                table         = tableDir,
                unique        = uniqueDir,
                index         = indexDir,
                historicTable = histTableDir,
                historicIndex = histIndexDir,
                historicUnique= histUniqueDir
            )
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun startFlows() {
        super.startFlows()

        this.launch {
            val cache = Cache()

            var clock = HLC()
            storeActorHasStarted.complete(Unit)
            try {
                for (storeAction in storeChannel) {
                    try {
                        clock = clock.calculateMaxTimeStamp()
                        @Suppress("UNCHECKED_CAST")
                        when (val request = storeAction.request) {
                            is AddRequest<*> ->
                                processAddRequest(clock, storeAction as AnyAddStoreAction)
                            is ChangeRequest<*> ->
                                processChangeRequest(clock, storeAction as AnyChangeStoreAction)
                            is DeleteRequest<*> ->
                                processDeleteRequest(clock, storeAction as AnyDeleteStoreAction, cache)
                            is GetRequest<*> ->
                                processGetRequest(storeAction as AnyGetStoreAction, cache)
                            is ScanRequest<*> ->
                                processScanRequest(storeAction as AnyScanStoreAction, cache)
                            is GetChangesRequest<*> ->
                                processGetChangesRequest(storeAction as AnyGetChangesStoreAction, cache)
                            is ScanChangesRequest<*> ->
                                processScanChangesRequest(storeAction as AnyScanChangesStoreAction, cache)
                            is GetUpdatesRequest<*> ->
                                processGetUpdatesRequest(storeAction as AnyGetUpdatesStoreAction, cache)
                            is ScanUpdatesRequest<*> ->
                                processScanUpdatesRequest(storeAction as AnyScanUpdatesStoreAction, cache)
                            is UpdateResponse<*> -> when (val update = (storeAction.request as UpdateResponse<*>).update) {
                                is AdditionUpdate<*> ->
                                    processAdditionUpdate(storeAction as AnyProcessUpdateResponseStoreAction)
                                is ChangeUpdate<*> ->
                                    processChangeUpdate(storeAction as AnyProcessUpdateResponseStoreAction)
                                is RemovalUpdate<*> ->
                                    processDeleteUpdate(storeAction as AnyProcessUpdateResponseStoreAction, cache)
                                is InitialChangesUpdate<*> ->
                                    processInitialChangesUpdate(storeAction as AnyProcessUpdateResponseStoreAction)
                                is InitialValuesUpdate<*> ->
                                    throw RequestException("Cannot process Values requests into data store since they do not contain all version information, do a changes request")
                                is OrderedKeysUpdate<*> ->
                                    throw RequestException("Cannot process Update requests into data store since they do not contain all change information, do a changes request")
                                else -> throw TypeException("Unknown update type $update for datastore processing")
                            }
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

    internal fun getTableDirs(dataModel: IsRootDataModel) =
        getTableDirs(dataModelIdsByString.getValue(dataModel.Meta.name))

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
            databaseOptionsSetter: DatabaseOptions.() -> Unit = {},
            migrationHandler: MigrationHandler<FoundationDBDataStore>? = null,
            versionUpdateHandler: VersionUpdateHandler<FoundationDBDataStore>? = null,
        ) = FoundationDBDataStore(
            keepAllVersions = keepAllVersions,
            fdbClusterFilePath = fdbClusterFilePath,
            directoryRootPath = directoryPath,
            tenantName = tenantName,
            dataModelsById = dataModelsById,
            onlyCheckModelVersion = onlyCheckModelVersion,
            databaseOptionsSetter = databaseOptionsSetter,
            migrationHandler = migrationHandler,
            versionUpdateHandler = versionUpdateHandler,
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
