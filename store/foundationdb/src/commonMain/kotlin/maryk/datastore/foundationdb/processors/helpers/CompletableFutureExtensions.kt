package maryk.datastore.foundationdb.processors.helpers

import com.apple.foundationdb.FDBException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException

/**
 * Await [CompletableFuture] results while surfacing [FDBException] directly so FoundationDB's retry
 * machinery can observe the failure. The standard [CompletableFuture.join] wraps failures in a
 * [CompletionException], which prevents [com.apple.foundationdb.TransactionContext.run] from
 * calling `onError` and retrying the transaction. By unwrapping the cause we make sure the retry
 * loop sees the original error and backoff logic applies consistently.
 */
internal fun <T> CompletableFuture<T>.awaitResult(): T {
    return try {
        this.join()
    } catch (error: Throwable) {
        throw error.unwrapFDBCause()
    }
}

private fun Throwable.unwrapFDBCause(): Throwable {
    val cause = this.cause ?: return this
    return when (cause) {
        is FDBException -> cause
        is RuntimeException -> cause
        is Error -> cause
        else -> this
    }
}
