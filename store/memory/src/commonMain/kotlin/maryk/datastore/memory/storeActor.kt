package maryk.datastore.memory

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.BroadcastChannel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import maryk.core.exceptions.DefNotFoundException
import maryk.core.models.IsRootValuesDataModel
import maryk.core.properties.PropertyDefinitions
import maryk.datastore.memory.records.DataStore
import maryk.datastore.shared.StoreAction
import maryk.datastore.shared.StoreActor

@UseExperimental(ExperimentalCoroutinesApi::class, FlowPreview::class)
internal fun CoroutineScope.storeActor(
    store: InMemoryDataStore,
    executor: StoreExecutor<*, *>
): StoreActor =
    BroadcastChannel<StoreAction<*, *, *, *>>(
        Channel.BUFFERED
    ).also {
        this.launch {
            val dataStores = mutableMapOf<UInt, DataStore<*, *>>()

            it.asFlow().collect { msg ->
                try {
                    val dataStoreFetcher = { model: IsRootValuesDataModel<*> ->
                        val index = store.dataModelIdsByString[model.name] ?: throw DefNotFoundException(model.name)
                        dataStores.getOrPut(index) {
                            DataStore<IsRootValuesDataModel<PropertyDefinitions>, PropertyDefinitions>(store.keepAllVersions)
                        }
                    }

                    executor(Unit, msg, dataStoreFetcher)
                } catch (e: Throwable) {
                    msg.response.completeExceptionally(e)
                }
            }
        }
    }
