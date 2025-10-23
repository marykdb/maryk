package maryk.datastore.foundationdb.processors.helpers

import com.apple.foundationdb.FDBException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException

/**
 * Await [CompletableFuture] results while surfacing [FDBException] directly so FoundationDB's retry
 * machinery can observe the failure.
 */
internal fun <T> CompletableFuture<T>.awaitResult(): T {
    return try {
        this.get()
    } catch (error: ExecutionException) {
        throw error.cause ?: error
    }
}
