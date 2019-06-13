@file:Suppress("EXPERIMENTAL_API_USAGE")

package maryk.datastore.rocksdb

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.actor

internal actual fun CoroutineScope.storeActor(
    store: RocksDBDataStore,
    executor: StoreExecutor
): StoreActor = actor {
    for (msg in channel) { // iterate over incoming messages
        try {
            executor(Unit, msg, store)
        } catch (e: Throwable) {
            msg.response.completeExceptionally(e)
        }
    }
}
