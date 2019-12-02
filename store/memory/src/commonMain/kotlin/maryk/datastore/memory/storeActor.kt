package maryk.datastore.memory

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.BroadcastChannel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import maryk.core.models.IsRootValuesDataModel
import maryk.core.properties.PropertyDefinitions
import maryk.datastore.memory.records.DataStore
import maryk.datastore.shared.StoreAction
import maryk.datastore.shared.StoreActor

@UseExperimental(ExperimentalCoroutinesApi::class, FlowPreview::class)
internal fun <DM : IsRootValuesDataModel<P>, P : PropertyDefinitions> CoroutineScope.storeActor(
    store: InMemoryDataStore,
    executor: StoreExecutor<DM, P>
): StoreActor<DM, P> =
    BroadcastChannel<StoreAction<DM, P, *, *>>(
        Channel.BUFFERED
    ).also {
        this.launch {
            val dataStore = DataStore<DM, P>(store.keepAllVersions)

            it.asFlow().collect { msg ->
                try {
                    executor(Unit, msg, dataStore)
                } catch (e: Throwable) {
                    msg.response.completeExceptionally(e)
                }
            }
        }
    }
