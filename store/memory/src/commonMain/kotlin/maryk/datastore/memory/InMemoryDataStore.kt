package maryk.datastore.memory

import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onStart
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
import maryk.datastore.memory.processors.AnyAddStoreAction
import maryk.datastore.memory.processors.AnyChangeStoreAction
import maryk.datastore.memory.processors.AnyDeleteStoreAction
import maryk.datastore.memory.processors.AnyGetChangesStoreAction
import maryk.datastore.memory.processors.AnyGetStoreAction
import maryk.datastore.memory.processors.AnyGetUpdatesStoreAction
import maryk.datastore.memory.processors.AnyProcessUpdateResponseStoreAction
import maryk.datastore.memory.processors.AnyScanChangesStoreAction
import maryk.datastore.memory.processors.AnyScanStoreAction
import maryk.datastore.memory.processors.AnyScanUpdatesStoreAction
import maryk.datastore.memory.processors.processAddRequest
import maryk.datastore.memory.processors.processAdditionUpdate
import maryk.datastore.memory.processors.processChangeRequest
import maryk.datastore.memory.processors.processChangeUpdate
import maryk.datastore.memory.processors.processDeleteRequest
import maryk.datastore.memory.processors.processDeleteUpdate
import maryk.datastore.memory.processors.processGetChangesRequest
import maryk.datastore.memory.processors.processGetRequest
import maryk.datastore.memory.processors.processGetUpdatesRequest
import maryk.datastore.memory.processors.processInitialChangesUpdate
import maryk.datastore.memory.processors.processScanChangesRequest
import maryk.datastore.memory.processors.processScanRequest
import maryk.datastore.memory.processors.processScanUpdatesRequest
import maryk.datastore.memory.records.DataStore
import maryk.datastore.shared.AbstractDataStore

/**
 * DataProcessor that stores all data changes in local memory.
 * Very useful for tests.
 */
class InMemoryDataStore(
    override val keepAllVersions: Boolean = false,
    dataModelsById: Map<UInt, IsRootDataModel>
) : AbstractDataStore(dataModelsById) {
    init {
        startFlows()
    }

    override fun startFlows() {
        super.startFlows()

        this.launch {
            val dataStores = mutableMapOf<UInt, DataStore<*>>()

            var clock = HLC()

            storeFlow
                .onStart { storeActorHasStarted.complete(Unit) }
                .collect { storeAction ->
                    try {
                        clock = clock.calculateMaxTimeStamp()

                        val dataStoreFetcher: (IsRootDataModel) -> DataStore<IsRootDataModel> = { model: IsRootDataModel ->
                            val index = dataModelIdsByString[model.Meta.name] ?: throw DefNotFoundException(model.Meta.name)
                            @Suppress("UNCHECKED_CAST")
                            dataStores.getOrPut(index) {
                                DataStore<IsRootDataModel>(keepAllVersions)
                            } as DataStore<IsRootDataModel>
                        }

                        @Suppress("UNCHECKED_CAST")
                        when (storeAction.request) {
                            is AddRequest<*> ->
                                processAddRequest(clock, storeAction as AnyAddStoreAction, dataStoreFetcher, updateSharedFlow)
                            is ChangeRequest<*> ->
                                processChangeRequest(clock, storeAction as AnyChangeStoreAction, dataStoreFetcher, updateSharedFlow)
                            is DeleteRequest<*> ->
                                processDeleteRequest(clock, storeAction as AnyDeleteStoreAction, dataStoreFetcher, updateSharedFlow)
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
                            is UpdateResponse<*> -> when(val update = (storeAction.request as UpdateResponse<*>).update) {
                                is AdditionUpdate<*> -> processAdditionUpdate(storeAction as AnyProcessUpdateResponseStoreAction, dataStoreFetcher, updateSharedFlow)
                                is ChangeUpdate<*> -> processChangeUpdate(storeAction as AnyProcessUpdateResponseStoreAction, dataStoreFetcher, updateSharedFlow)
                                is RemovalUpdate<*> -> processDeleteUpdate(storeAction as AnyProcessUpdateResponseStoreAction, dataStoreFetcher, updateSharedFlow)
                                is InitialChangesUpdate<*> -> processInitialChangesUpdate(storeAction as AnyProcessUpdateResponseStoreAction, dataStoreFetcher, updateSharedFlow)
                                is InitialValuesUpdate<*> -> throw RequestException("Cannot process Values requests into data store since they do not contain all version information, do a changes request")
                                is OrderedKeysUpdate<*> -> throw RequestException("Cannot process Update requests into data store since they do not contain all change information, do a changes request")
                                else -> throw TypeException("Unknown update type $update for datastore processing")
                            }
                            else -> throw TypeException("Unknown request type ${storeAction.request}")
                        }
                    } catch (e: Throwable) {
                        storeAction.response.completeExceptionally(e)
                    }
                }
        }
    }
}
