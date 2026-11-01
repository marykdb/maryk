package maryk.datastore.indexeddb

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Web Locks reduce contention; the fenced IndexedDB lease still guards every writer.
 * Inline the native callbacks so each write avoids a bundle of capturing adapters.
 */
internal suspend inline fun <T> withIndexedDbWebLock(
    webLocksAvailable: Boolean,
    crossinline requestLock: (onAcquired: (() -> Unit) -> Unit, onError: (String) -> Unit) -> Unit,
    crossinline withLease: suspend () -> T,
): T {
    if (!webLocksAvailable) {
        return withLease()
    }

    return suspendCancellableCoroutine { continuation ->
        requestLock(
            { release ->
                if (!continuation.isActive) {
                    release()
                    return@requestLock
                }
                val webLock = IndexedDbWebLockRelease(release)
                var blockJob: Job? = null
                continuation.invokeOnCancellation {
                    blockJob?.cancel()
                    webLock.releaseOnce()
                }
                blockJob = CoroutineScope(continuation.context).launch {
                    try {
                        continuation.resume(withLease())
                    } catch (cause: Throwable) {
                        continuation.resumeWithException(cause)
                    } finally {
                        webLock.releaseOnce()
                    }
                }
            },
            {
                if (!continuation.isActive) return@requestLock
                val fallbackJob = CoroutineScope(continuation.context).launch {
                    try {
                        continuation.resume(withLease())
                    } catch (cause: Throwable) {
                        continuation.resumeWithException(cause)
                    }
                }
                continuation.invokeOnCancellation { fallbackJob.cancel() }
            },
        )
    }
}

/** Native callbacks keep lease acquisition, renewal and write fencing in IndexedDB transactions. */
internal suspend inline fun <T> withIndexedDbLease(
    databaseName: String,
    ownerId: String,
    crossinline tryAcquire: suspend () -> Boolean,
    crossinline awaitSignal: suspend () -> Unit,
    crossinline renew: suspend () -> Boolean,
    crossinline release: suspend () -> Unit,
    crossinline signalRelease: () -> Unit,
    crossinline block: suspend (String) -> T,
): T {
    while (!tryAcquire()) {
        awaitSignal()
    }

    return coroutineScope {
        var leaseFailure: Throwable? = null
        val renewal = launch {
            while (isActive) {
                delay(writeLeaseRenewIntervalMillis)
                if (!renew()) {
                    leaseFailure = IllegalStateException("Lost IndexedDB write lease for $databaseName")
                    break
                }
            }
        }

        try {
            val result = block(ownerId)
            leaseFailure?.let { throw it }
            result
        } finally {
            withContext(NonCancellable) {
                renewal.cancelAndJoin()
                release()
                signalRelease()
            }
        }
    }
}

internal suspend fun awaitLeaseTransaction(
    start: ((Boolean) -> Unit, (String) -> Unit, (() -> Unit) -> Unit) -> Unit,
    onLateAcquire: () -> Unit = {},
): Boolean = suspendCancellableCoroutine { continuation ->
    var cancelTransaction: (() -> Unit)? = null
    var transactionCompleted = false
    var acquired = false
    var lateAcquireHandled = false
    fun releaseLateAcquire() {
        if (!lateAcquireHandled) {
            lateAcquireHandled = true
            onLateAcquire()
        }
    }
    continuation.invokeOnCancellation {
        cancelTransaction?.invoke()
        if (transactionCompleted && acquired) releaseLateAcquire()
    }
    start(
        { didAcquire ->
            transactionCompleted = true
            acquired = didAcquire
            if (continuation.isActive) {
                continuation.resume(didAcquire) { _, _, _ ->
                    if (didAcquire) releaseLateAcquire()
                }
            } else if (didAcquire) {
                releaseLateAcquire()
            }
        },
        { message ->
            if (continuation.isActive) {
                continuation.resumeWithException(IllegalStateException(message))
            }
        },
        { cancel ->
            cancelTransaction = cancel
            if (!continuation.isActive) cancel()
        },
    )
}

private const val writeLeaseRenewIntervalMillis = 10_000L

/** Replaces the captured release flag without allocating another callback. */
internal class IndexedDbWebLockRelease(private val release: () -> Unit) {
    private var released = false

    fun releaseOnce() {
        if (!released) {
            released = true
            release()
        }
    }
}
