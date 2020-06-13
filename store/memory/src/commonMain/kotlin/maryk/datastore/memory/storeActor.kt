package maryk.datastore.memory

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.BroadcastChannel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import maryk.core.exceptions.DefNotFoundException
import maryk.core.models.IsRootValuesDataModel
import maryk.core.properties.PropertyDefinitions
import maryk.datastore.memory.records.DataStore
import maryk.datastore.shared.StoreAction
import maryk.datastore.shared.StoreActor
import maryk.datastore.shared.updates.Update

@OptIn(
    ExperimentalCoroutinesApi::class, FlowPreview::class
)
internal fun CoroutineScope.storeActor(
    store: InMemoryDataStore,
    hasStarted: CompletableDeferred<Unit>,
    executor: StoreExecutor<*, *>
): StoreActor =
    BroadcastChannel<StoreAction<*, *, *, *>>(
        Channel.BUFFERED
    ).also {
        this.launch {
            val dataStores = mutableMapOf<UInt, DataStore<*, *>>()

            it.asFlow().onStart { hasStarted.complete(Unit) }.collect { msg ->
                try {
                    val dataStoreFetcher = { model: IsRootValuesDataModel<*> ->
                        val index = store.dataModelIdsByString[model.name] ?: throw DefNotFoundException(model.name)
                        dataStores.getOrPut(index) {
                            DataStore<IsRootValuesDataModel<PropertyDefinitions>, PropertyDefinitions>(store.keepAllVersions)
                        }
                    }

                    @Suppress("UNCHECKED_CAST")
                    executor(Unit, msg, dataStoreFetcher, store.updateSendChannel as SendChannel<Update<*, *>>)
                } catch (e: Throwable) {
                    msg.response.completeExceptionally(e)
                }
            }
        }
    }
