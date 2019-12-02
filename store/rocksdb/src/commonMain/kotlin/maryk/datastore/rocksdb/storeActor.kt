package maryk.datastore.rocksdb

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.BroadcastChannel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import maryk.datastore.shared.StoreAction
import maryk.datastore.shared.StoreActor

@UseExperimental(ExperimentalCoroutinesApi::class, FlowPreview::class)
internal fun CoroutineScope.storeActor(
    store: RocksDBDataStore,
    executor: StoreExecutor
): StoreActor<*, *> = BroadcastChannel<StoreAction<*, *, *, *>>(
    Channel.BUFFERED
).also {
    this.launch {
        it.asFlow().collect { msg ->
            try {
                executor(Unit, msg, store)
            } catch (e: Throwable) {
                msg.response.completeExceptionally(e)
            }
        }
    }
}
