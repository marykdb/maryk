package maryk.datastore.memory

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import maryk.core.exceptions.DefNotFoundException
import maryk.core.models.IsRootValuesDataModel
import maryk.core.models.RootDataModel
import maryk.core.properties.PropertyDefinitions
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
@OptIn(FlowPreview::class)
class InMemoryDataStore(
    override val keepAllVersions: Boolean = false,
    dataModelsById: Map<UInt, RootDataModel<*, *>>
) : AbstractDataStore(dataModelsById) {
    @ExperimentalCoroutinesApi
    override fun startFlows() {
        super.startFlows()

        this.launch {
            val dataStores = mutableMapOf<UInt, DataStore<*, *>>()

            storeChannel.asFlow()
                .onStart { storeActorHasStarted.complete(Unit) }
                .collect { msg ->
                    try {
                        val dataStoreFetcher = { model: IsRootValuesDataModel<*> ->
                            val index = dataModelIdsByString[model.name] ?: throw DefNotFoundException(model.name)
                            dataStores.getOrPut(index) {
                                DataStore<IsRootValuesDataModel<PropertyDefinitions>, PropertyDefinitions>(keepAllVersions)
                            }
                        }

                        storeExecutor(Unit, msg, dataStoreFetcher, updateSendChannel)
                    } catch (e: Throwable) {
                        msg.response.completeExceptionally(e)
                    }
                }
        }
    }
}
