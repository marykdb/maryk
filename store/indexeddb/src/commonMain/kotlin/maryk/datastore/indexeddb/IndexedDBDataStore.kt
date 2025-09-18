package maryk.datastore.indexeddb

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import maryk.core.clock.HLC
import maryk.core.exceptions.DefNotFoundException
import maryk.core.exceptions.RequestException
import maryk.core.exceptions.TypeException
import maryk.core.models.IsRootDataModel
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
import maryk.datastore.indexeddb.persistence.toDataStore
import maryk.datastore.indexeddb.persistence.toPersistedDataStore
import maryk.datastore.indexeddb.processors.AnyAddStoreAction
import maryk.datastore.indexeddb.processors.AnyChangeStoreAction
import maryk.datastore.indexeddb.processors.AnyDeleteStoreAction
import maryk.datastore.indexeddb.processors.AnyGetChangesStoreAction
import maryk.datastore.indexeddb.processors.AnyGetStoreAction
import maryk.datastore.indexeddb.processors.AnyGetUpdatesStoreAction
import maryk.datastore.indexeddb.processors.AnyProcessUpdateResponseStoreAction
import maryk.datastore.indexeddb.processors.AnyScanChangesStoreAction
import maryk.datastore.indexeddb.processors.AnyScanStoreAction
import maryk.datastore.indexeddb.processors.AnyScanUpdatesStoreAction
import maryk.datastore.indexeddb.processors.processAddRequest
import maryk.datastore.indexeddb.processors.processAdditionUpdate
import maryk.datastore.indexeddb.processors.processChangeRequest
import maryk.datastore.indexeddb.processors.processChangeUpdate
import maryk.datastore.indexeddb.processors.processDeleteRequest
import maryk.datastore.indexeddb.processors.processDeleteUpdate
import maryk.datastore.indexeddb.processors.processGetChangesRequest
import maryk.datastore.indexeddb.processors.processGetRequest
import maryk.datastore.indexeddb.processors.processGetUpdatesRequest
import maryk.datastore.indexeddb.processors.processInitialChangesUpdate
import maryk.datastore.indexeddb.processors.processScanChangesRequest
import maryk.datastore.indexeddb.processors.processScanRequest
import maryk.datastore.indexeddb.processors.processScanUpdatesRequest
import maryk.datastore.indexeddb.records.DataStore
import maryk.datastore.shared.AbstractDataStore
import maryk.datastore.shared.DISPATCHER

class IndexedDBDataStore private constructor(
    override val keepAllVersions: Boolean,
    dataModelsById: Map<UInt, IsRootDataModel>,
    private val driver: IndexedDbDriver,
) : AbstractDataStore(dataModelsById, DISPATCHER) {
    override val supportsFuzzyQualifierFiltering: Boolean = true
    override val supportsSubReferenceFiltering: Boolean = true

    init {
        startFlows()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun startFlows() {
        super.startFlows()

        this.launch {
            val dataStoresById = mutableMapOf<UInt, DataStore<IsRootDataModel>>()

            for ((index, dataModel) in dataModelsById) {
                val persisted = driver.loadStore(storeNameFor(dataModel))
                val dataStore = persisted?.toDataStore(dataModel, keepAllVersions)
                    ?: DataStore(keepAllVersions)
                @Suppress("UNCHECKED_CAST")
                dataStoresById[index] = dataStore
            }

            val dataStoreFetcher: (IsRootDataModel) -> DataStore<IsRootDataModel> = { model ->
                val index = dataModelIdsByString[model.Meta.name]
                    ?: throw DefNotFoundException(model.Meta.name)
                @Suppress("UNCHECKED_CAST")
                dataStoresById.getOrPut(index) { DataStore(keepAllVersions) }
            }
            suspend fun persistDataModel(model: IsRootDataModel) {
                val index = dataModelIdsByString[model.Meta.name] ?: return
                @Suppress("UNCHECKED_CAST")
                val dataStore = dataStoresById[index] ?: return
                driver.writeStore(
                    storeName = storeNameFor(model),
                    store = dataStore.toPersistedDataStore(model)
                )
            }

            var clock = HLC()

            storeActorHasStarted.complete(Unit)
            try {
                for (storeAction in storeChannel) {
                    try {
                        clock = clock.calculateMaxTimeStamp()

                        @Suppress("UNCHECKED_CAST")
                        when (val request = storeAction.request) {
                            is AddRequest<*> -> {
                                processAddRequest(
                                    clock,
                                    storeAction as AnyAddStoreAction,
                                    dataStoreFetcher,
                                    updateSharedFlow
                                )
                                persistDataModel(request.dataModel)
                            }
                            is ChangeRequest<*> -> {
                                processChangeRequest(
                                    clock,
                                    storeAction as AnyChangeStoreAction,
                                    dataStoreFetcher,
                                    updateSharedFlow
                                )
                                persistDataModel(request.dataModel)
                            }
                            is DeleteRequest<*> -> {
                                processDeleteRequest(
                                    clock,
                                    storeAction as AnyDeleteStoreAction,
                                    dataStoreFetcher,
                                    updateSharedFlow
                                )
                                persistDataModel(request.dataModel)
                            }
                            is GetRequest<*> ->
                                processGetRequest(storeAction as AnyGetStoreAction, dataStoreFetcher)
                            is GetChangesRequest<*> ->
                                processGetChangesRequest(storeAction as AnyGetChangesStoreAction, dataStoreFetcher)
                            is GetUpdatesRequest<*> ->
                                processGetUpdatesRequest(storeAction as AnyGetUpdatesStoreAction, dataStoreFetcher)
                            is ScanRequest<*> ->
                                processScanRequest(storeAction as AnyScanStoreAction, dataStoreFetcher)
                            is ScanChangesRequest<*> ->
                                processScanChangesRequest(storeAction as AnyScanChangesStoreAction, dataStoreFetcher)
                            is ScanUpdatesRequest<*> ->
                                processScanUpdatesRequest(storeAction as AnyScanUpdatesStoreAction, dataStoreFetcher)
                            is UpdateResponse<*> -> when (val update = request.update) {
                                is AdditionUpdate<*> -> {
                                    processAdditionUpdate(
                                        storeAction as AnyProcessUpdateResponseStoreAction,
                                        dataStoreFetcher,
                                        updateSharedFlow
                                    )
                                    persistDataModel(request.dataModel)
                                }
                                is ChangeUpdate<*> -> {
                                    processChangeUpdate(
                                        storeAction as AnyProcessUpdateResponseStoreAction,
                                        dataStoreFetcher,
                                        updateSharedFlow
                                    )
                                    persistDataModel(request.dataModel)
                                }
                                is RemovalUpdate<*> -> {
                                    processDeleteUpdate(
                                        storeAction as AnyProcessUpdateResponseStoreAction,
                                        dataStoreFetcher,
                                        updateSharedFlow
                                    )
                                    persistDataModel(request.dataModel)
                                }
                                is InitialChangesUpdate<*> -> {
                                    processInitialChangesUpdate(
                                        storeAction as AnyProcessUpdateResponseStoreAction,
                                        dataStoreFetcher,
                                        updateSharedFlow
                                    )
                                    persistDataModel(request.dataModel)
                                }
                                is InitialValuesUpdate<*> -> throw RequestException(
                                    "Cannot process Values requests into data store since they do not contain all version information, do a changes request"
                                )
                                is OrderedKeysUpdate<*> -> throw RequestException(
                                    "Cannot process Update requests into data store since they do not contain all change information, do a changes request"
                                )
                                else -> throw TypeException("Unknown update type $update for datastore processing")
                            }
                            else -> throw TypeException("Unknown request type $request")
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

    override suspend fun close() {
        super.close()
        driver.close()
    }

    companion object {
        private const val DEFAULT_DATABASE_VERSION = 1

        suspend fun open(
            databaseName: String,
            dataModelsById: Map<UInt, IsRootDataModel>,
            keepAllVersions: Boolean = false,
            fallbackToMemoryStore: Boolean = false,
        ): IndexedDBDataStore {
            val driver = openIndexedDbDriver(
                name = databaseName,
                version = DEFAULT_DATABASE_VERSION,
                storeNames = dataModelsById.values.map(::storeNameFor),
                fallbackToMemoryStore = fallbackToMemoryStore,
            )

            return IndexedDBDataStore(
                keepAllVersions = keepAllVersions,
                dataModelsById = dataModelsById,
                driver = driver,
            )
        }
    }
}

private fun storeNameFor(dataModel: IsRootDataModel) = dataModel.Meta.name
