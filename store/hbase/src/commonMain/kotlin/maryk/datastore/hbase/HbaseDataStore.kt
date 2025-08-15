package maryk.datastore.hbase

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.future.await
import kotlinx.coroutines.launch
import maryk.core.clock.HLC
import maryk.core.exceptions.RequestException
import maryk.core.exceptions.TypeException
import maryk.core.models.IsRootDataModel
import maryk.core.models.migration.MigrationException
import maryk.core.models.migration.MigrationHandler
import maryk.core.models.migration.MigrationStatus
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
import maryk.datastore.hbase.helpers.deleteCompleteIndexContents
import maryk.datastore.hbase.helpers.walkDataRecordsAndFillIndex
import maryk.datastore.hbase.model.checkModelIfMigrationIsNeeded
import maryk.datastore.hbase.model.storeModelDefinition
import maryk.datastore.hbase.processors.AnyAddStoreAction
import maryk.datastore.hbase.processors.AnyChangeStoreAction
import maryk.datastore.hbase.processors.AnyDeleteStoreAction
import maryk.datastore.hbase.processors.AnyGetChangesStoreAction
import maryk.datastore.hbase.processors.AnyGetStoreAction
import maryk.datastore.hbase.processors.AnyGetUpdatesStoreAction
import maryk.datastore.hbase.processors.AnyProcessUpdateResponseStoreAction
import maryk.datastore.hbase.processors.AnyScanChangesStoreAction
import maryk.datastore.hbase.processors.AnyScanStoreAction
import maryk.datastore.hbase.processors.AnyScanUpdatesStoreAction
import maryk.datastore.hbase.processors.processAddRequest
import maryk.datastore.hbase.processors.processAdditionUpdate
import maryk.datastore.hbase.processors.processChangeRequest
import maryk.datastore.hbase.processors.processChangeUpdate
import maryk.datastore.hbase.processors.processDeleteRequest
import maryk.datastore.hbase.processors.processDeleteUpdate
import maryk.datastore.hbase.processors.processGetChangesRequest
import maryk.datastore.hbase.processors.processGetRequest
import maryk.datastore.hbase.processors.processGetUpdatesRequest
import maryk.datastore.hbase.processors.processInitialChangesUpdate
import maryk.datastore.hbase.processors.processScanChangesRequest
import maryk.datastore.hbase.processors.processScanRequest
import maryk.datastore.hbase.processors.processScanUpdatesRequest
import maryk.datastore.shared.AbstractDataStore
import maryk.datastore.shared.Cache
import org.apache.hadoop.hbase.NamespaceDescriptor
import org.apache.hadoop.hbase.NamespaceNotFoundException
import org.apache.hadoop.hbase.TableName
import org.apache.hadoop.hbase.client.AdvancedScanResultConsumer
import org.apache.hadoop.hbase.client.AsyncAdmin
import org.apache.hadoop.hbase.client.AsyncConnection
import org.apache.hadoop.hbase.client.AsyncTable

class HbaseDataStore private constructor(
    override val keepAllVersions: Boolean = true,
    val connection: AsyncConnection,
    val namespace: ByteArray = NamespaceDescriptor.DEFAULT_NAMESPACE_NAME,
    dataModelsById: Map<UInt, IsRootDataModel>,
    private val onlyCheckModelVersion: Boolean = false,
    val migrationHandler: MigrationHandler<HbaseDataStore>? = null,
    val versionUpdateHandler: VersionUpdateHandler<HbaseDataStore>? = null,
): AbstractDataStore(dataModelsById) {
    private val scheduledVersionUpdateHandlers = mutableListOf<suspend () -> Unit>()
    private val tableNameByDataModelName = mutableMapOf<String, TableName>()

    override val supportsFuzzyQualifierFiltering: Boolean = false
    override val supportsSubReferenceFiltering: Boolean = false

    private suspend fun initAsync() {
        val admin = connection.admin

        val namespaceAsString = namespace.decodeToString()
        try {
            admin.getNamespaceDescriptor(namespaceAsString).await()
        } catch (_: NamespaceNotFoundException) {
            admin.createNamespace(
                NamespaceDescriptor.create(namespaceAsString).build()
            ).await()
        }

        val conversionContext = DefinitionsConversionContext()

        for (dataModel in dataModelsById.values) {
            val tableName = getTableName(dataModel)
            val tableDescriptor = admin.getDescriptor(tableName)
            when (val migrationStatus = checkModelIfMigrationIsNeeded(tableDescriptor, dataModel, onlyCheckModelVersion, conversionContext)) {
                MigrationStatus.UpToDate, MigrationStatus.AlreadyProcessed -> Unit // Do nothing since no work is needed
                MigrationStatus.NewModel -> {
                    scheduledVersionUpdateHandlers.add {
                        versionUpdateHandler?.invoke(this@HbaseDataStore, null, dataModel)

                        storeModelDefinition(admin, null, dataModel, keepAllVersions)
                    }
                }
                is MigrationStatus.OnlySafeAdds -> {
                    scheduledVersionUpdateHandlers.add {
                        versionUpdateHandler?.invoke(this@HbaseDataStore, migrationStatus.storedDataModel as StoredRootDataModelDefinition, dataModel)
                        storeModelDefinition(admin, tableDescriptor.await(), dataModel, keepAllVersions)
                    }
                }
                is MigrationStatus.NewIndicesOnExistingProperties -> {
                    fillIndex(admin, dataModel, migrationStatus.indicesToIndex)
                    scheduledVersionUpdateHandlers.add {
                        versionUpdateHandler?.invoke(this@HbaseDataStore, migrationStatus.storedDataModel as StoredRootDataModelDefinition, dataModel)
                        storeModelDefinition(admin, tableDescriptor.await(), dataModel, keepAllVersions)
                    }
                }
                is MigrationStatus.NeedsMigration -> {
                    val succeeded = migrationHandler?.invoke(this@HbaseDataStore, migrationStatus.storedDataModel as StoredRootDataModelDefinition, dataModel)
                        ?: throw MigrationException("Migration needed: No migration handler present. \n$migrationStatus")

                    if (!succeeded) {
                        throw MigrationException("Migration could not be handled for ${dataModel.Meta.name} & ${(migrationStatus.storedDataModel as? StoredRootDataModelDefinition)?.Meta?.version}\n$migrationStatus")
                    }

                    migrationStatus.indicesToIndex?.let {
                        fillIndex(admin, dataModel, it)
                    }
                    scheduledVersionUpdateHandlers.add {
                        versionUpdateHandler?.invoke(this@HbaseDataStore, migrationStatus.storedDataModel as StoredRootDataModelDefinition, dataModel)
                        storeModelDefinition(admin, tableDescriptor.await(), dataModel, keepAllVersions)
                    }
                }
            }
        }

        startFlows()

        scheduledVersionUpdateHandlers.forEach { it() }
    }

    override fun startFlows() {
        super.startFlows()

        this.launch {
            val cache = Cache()

            var clock = HLC()
            storeActorHasStarted.complete(Unit)
            for (storeAction in storeChannel) {
                try {
                    clock = clock.calculateMaxTimeStamp()

                    @Suppress("UNCHECKED_CAST")
                    when (storeAction.request) {
                        is AddRequest<*> ->
                            processAddRequest(clock, storeAction as AnyAddStoreAction, this@HbaseDataStore, updateSharedFlow)
                        is ChangeRequest<*> ->
                            processChangeRequest(clock, storeAction as AnyChangeStoreAction, this@HbaseDataStore, updateSharedFlow)
                        is DeleteRequest<*> ->
                            processDeleteRequest(clock, storeAction as AnyDeleteStoreAction, this@HbaseDataStore, cache, updateSharedFlow)
                        is GetRequest<*> ->
                            processGetRequest(storeAction as AnyGetStoreAction, this@HbaseDataStore, cache)
                        is GetChangesRequest<*> ->
                            processGetChangesRequest(storeAction as AnyGetChangesStoreAction, this@HbaseDataStore, cache)
                        is GetUpdatesRequest<*> ->
                            processGetUpdatesRequest(storeAction as AnyGetUpdatesStoreAction, this@HbaseDataStore, cache)
                        is ScanRequest<*> ->
                            processScanRequest(storeAction as AnyScanStoreAction, this@HbaseDataStore, cache)
                        is ScanChangesRequest<*> ->
                            processScanChangesRequest(storeAction as AnyScanChangesStoreAction, this@HbaseDataStore, cache)
                        is ScanUpdatesRequest<*> ->
                            processScanUpdatesRequest(storeAction as AnyScanUpdatesStoreAction, this@HbaseDataStore, cache)
                        is UpdateResponse<*> -> when(val update = (storeAction.request as UpdateResponse<*>).update) {
                            is AdditionUpdate<*> -> processAdditionUpdate(storeAction as AnyProcessUpdateResponseStoreAction, this@HbaseDataStore, updateSharedFlow)
                            is ChangeUpdate<*> -> processChangeUpdate(storeAction as AnyProcessUpdateResponseStoreAction, this@HbaseDataStore, updateSharedFlow)
                            is RemovalUpdate<*> -> processDeleteUpdate(storeAction as AnyProcessUpdateResponseStoreAction, this@HbaseDataStore, cache, updateSharedFlow)
                            is InitialChangesUpdate<*> -> processInitialChangesUpdate(storeAction as AnyProcessUpdateResponseStoreAction, this@HbaseDataStore, updateSharedFlow)
                            is InitialValuesUpdate<*> -> throw RequestException("Cannot process Values requests into data store since they do not contain all version information, do a changes request")
                            is OrderedKeysUpdate<*> -> throw RequestException("Cannot process Update requests into data store since they do not contain all change information, do a changes request")
                            else -> throw TypeException("Unknown update type $update for datastore processing")
                        }
                        else -> throw TypeException("Unknown request type ${storeAction.request}")
                    }
                } catch (e: CancellationException) {
                    storeAction.response.cancel(e)
                    throw e
                } catch (e: Throwable) {
                    storeAction.response.completeExceptionally(e)
                }
            }
        }
    }

    fun <DM: IsRootDataModel> getTableName(dataModel: DM): TableName =
        tableNameByDataModelName.getOrPut(dataModel.Meta.name) { TableName.valueOf(namespace, dataModel.Meta.name.encodeToByteArray()) }

    fun <DM: IsRootDataModel> getTable(dataModel: DM): AsyncTable<AdvancedScanResultConsumer> =
        connection.getTable(getTableName(dataModel))

    /** Walk all current values and fill [indicesToIndex] */
    private suspend fun fillIndex(
        admin: AsyncAdmin,
        dataModel: IsRootDataModel,
        indicesToIndex: List<IsIndexable>,
    ) {
        val tableName = getTableName(dataModel)

        for (indexable in indicesToIndex) {
            deleteCompleteIndexContents(admin, tableName, indexable)
        }

        val table = getTable(dataModel)
        walkDataRecordsAndFillIndex(admin, table, keepAllVersions, indicesToIndex)
    }

    companion object {
        suspend fun open(
            keepAllVersions: Boolean = true,
            connection: AsyncConnection,
            namespace: ByteArray = NamespaceDescriptor.DEFAULT_NAMESPACE_NAME,
            dataModelsById: Map<UInt, IsRootDataModel>,
            onlyCheckModelVersion: Boolean = false,
            migrationHandler: MigrationHandler<HbaseDataStore>? = null,
            versionUpdateHandler: VersionUpdateHandler<HbaseDataStore>? = null
        ): HbaseDataStore {
            return HbaseDataStore(
                keepAllVersions = keepAllVersions,
                connection = connection,
                namespace = namespace,
                dataModelsById = dataModelsById,
                onlyCheckModelVersion = onlyCheckModelVersion,
                migrationHandler = migrationHandler,
                versionUpdateHandler = versionUpdateHandler
            ).apply { initAsync() }
        }
    }
}
