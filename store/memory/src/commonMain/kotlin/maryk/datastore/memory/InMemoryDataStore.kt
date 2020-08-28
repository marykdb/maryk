package maryk.datastore.memory

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import maryk.core.clock.HLC
import maryk.core.exceptions.DefNotFoundException
import maryk.core.exceptions.TypeException
import maryk.core.models.IsRootValuesDataModel
import maryk.core.models.RootDataModel
import maryk.core.properties.PropertyDefinitions
import maryk.core.query.requests.AddRequest
import maryk.core.query.requests.ChangeRequest
import maryk.core.query.requests.DeleteRequest
import maryk.core.query.requests.GetChangesRequest
import maryk.core.query.requests.GetRequest
import maryk.core.query.requests.GetUpdatesRequest
import maryk.core.query.requests.ScanChangesRequest
import maryk.core.query.requests.ScanRequest
import maryk.core.query.requests.ScanUpdatesRequest
import maryk.core.query.responses.updates.AdditionUpdate
import maryk.core.query.responses.updates.ChangeUpdate
import maryk.core.query.responses.updates.RemovalUpdate
import maryk.core.services.responses.UpdateResponse
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
import maryk.datastore.memory.processors.processScanChangesRequest
import maryk.datastore.memory.processors.processScanRequest
import maryk.datastore.memory.processors.processScanUpdatesRequest
import maryk.datastore.memory.records.DataStore
import maryk.datastore.shared.AbstractDataStore
import maryk.datastore.shared.StoreAction
import maryk.datastore.shared.updates.Update

internal typealias StoreExecutor<DM, P> = suspend Unit.(
    StoreAction<DM, P, *, *>,
    dataStoreFetcher: (IsRootValuesDataModel<P>) -> DataStore<DM, P>,
    updateSendChannel: SendChannel<Update<DM, P>>
) -> Unit

/**
 * DataProcessor that stores all data changes in local memory.
 * Very useful for tests.
 */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class InMemoryDataStore(
    override val keepAllVersions: Boolean = false,
    dataModelsById: Map<UInt, RootDataModel<*, *>>
) : AbstractDataStore(dataModelsById) {
    init {
        startFlows()
    }

    override fun startFlows() {
        super.startFlows()

        this.launch {
            val dataStores = mutableMapOf<UInt, DataStore<*, *>>()

            var clock = HLC()

            storeChannel.asFlow()
                .onStart { storeActorHasStarted.complete(Unit) }
                .collect { storeAction ->
                    try {
                        clock = clock.calculateMaxTimeStamp()

                        val dataStoreFetcher = { model: IsRootValuesDataModel<*> ->
                            val index = dataModelIdsByString[model.name] ?: throw DefNotFoundException(model.name)
                            dataStores.getOrPut(index) {
                                DataStore<IsRootValuesDataModel<PropertyDefinitions>, PropertyDefinitions>(keepAllVersions)
                            }
                        }

                        @Suppress("UNCHECKED_CAST")
                        when (storeAction.request) {
                            is AddRequest<*, *> ->
                                processAddRequest(clock, storeAction as AnyAddStoreAction, dataStoreFetcher, updateSendChannel)
                            is ChangeRequest<*> ->
                                processChangeRequest(clock, storeAction as AnyChangeStoreAction, dataStoreFetcher, updateSendChannel)
                            is DeleteRequest<*> ->
                                processDeleteRequest(clock, storeAction as AnyDeleteStoreAction, dataStoreFetcher, updateSendChannel)
                            is GetRequest<*, *> ->
                                processGetRequest(storeAction as AnyGetStoreAction, dataStoreFetcher)
                            is GetChangesRequest<*, *> ->
                                processGetChangesRequest(storeAction as AnyGetChangesStoreAction, dataStoreFetcher)
                            is GetUpdatesRequest<*, *> ->
                                processGetUpdatesRequest(storeAction as AnyGetUpdatesStoreAction, dataStoreFetcher)
                            is ScanRequest<*, *> ->
                                processScanRequest(storeAction as AnyScanStoreAction, dataStoreFetcher)
                            is ScanChangesRequest<*, *> ->
                                processScanChangesRequest(storeAction as AnyScanChangesStoreAction, dataStoreFetcher)
                            is ScanUpdatesRequest<*, *> ->
                                processScanUpdatesRequest(storeAction as AnyScanUpdatesStoreAction, dataStoreFetcher)
                            is UpdateResponse<*, *> -> when(val update = (storeAction.request as UpdateResponse<*, *>).update) {
                                is AdditionUpdate<*, *> -> processAdditionUpdate(storeAction as AnyProcessUpdateResponseStoreAction, dataStoreFetcher, updateSendChannel)
                                is ChangeUpdate<*, *> -> processChangeUpdate(storeAction as AnyProcessUpdateResponseStoreAction, dataStoreFetcher, updateSendChannel)
                                is RemovalUpdate<*, *> -> processDeleteUpdate(storeAction as AnyProcessUpdateResponseStoreAction, dataStoreFetcher, updateSendChannel)
                                else -> throw TypeException("Unknown update type ${update} for datastore processing")
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
