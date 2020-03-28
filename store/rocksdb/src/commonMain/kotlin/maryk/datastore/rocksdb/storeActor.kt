package maryk.datastore.rocksdb

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.BroadcastChannel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import maryk.datastore.shared.StoreAction
import maryk.datastore.shared.StoreActor
import maryk.datastore.shared.updates.Update

@OptIn(
    ExperimentalCoroutinesApi::class, FlowPreview::class
)
internal fun CoroutineScope.storeActor(
    store: RocksDBDataStore,
    executor: StoreExecutor
): StoreActor = BroadcastChannel<StoreAction<*, *, *, *>>(
    Channel.BUFFERED
).also {
    this.launch {
        it.asFlow().collect { msg ->
            try {
                @Suppress("UNCHECKED_CAST")
                executor(Unit, msg, store, store.updateSendChannel as SendChannel<Update<*, *>>)
            } catch (e: Throwable) {
                msg.response.completeExceptionally(e)
            }
        }
    }
}
