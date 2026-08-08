@file:Suppress("UnsafeCastFromDynamic")

package maryk.datastore.indexeddb

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
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

internal actual suspend fun openPlatformIndexedDbByteStore(
    databaseName: String,
    objectStoreNames: Set<String>,
    version: Int,
): IndexedDbByteStore {
    val requiredStoreNames = objectStoreNames + writeLeaseStoreName
    var database = openDatabase(databaseName, requiredStoreNames, version)
    val missingStoreNames = missingObjectStoreNames(database, requiredStoreNames)
    if (missingStoreNames.isNotEmpty()) {
        val nextVersion = database.version.unsafeCast<Int>() + 1
        database.close()
        database = openDatabase(databaseName, requiredStoreNames, nextVersion)
    }
    return BrowserIndexedDbByteStore(database, databaseName, "maryk-indexeddb:$databaseName")
}

private suspend fun openDatabase(
    databaseName: String,
    objectStoreNames: Set<String>,
    version: Int,
): dynamic {
    val request = if (version == 1) {
        indexedDB.open(databaseName)
    } else {
        indexedDB.open(databaseName, version)
    }

    request.onupgradeneeded = {
        val database = request.result
        for (storeName in objectStoreNames.sorted()) {
            if (!database.objectStoreNames.contains(storeName)) {
                database.createObjectStore(storeName)
            }
        }
    }

    val database = awaitOpenResult(request).unsafeCast<dynamic>()
    database.onversionchange = { _: dynamic ->
        database.close()
    }
    return database
}

private fun missingObjectStoreNames(database: dynamic, objectStoreNames: Set<String>): List<String> =
    objectStoreNames.sorted().filter { storeName ->
        !objectStoreNamesContains(database, storeName)
    }

private fun objectStoreNamesContains(database: dynamic, storeName: String): Boolean =
    database.objectStoreNames.contains(storeName).unsafeCast<Boolean>()

private class BrowserIndexedDbByteStore(
    private val database: dynamic,
    private val databaseName: String,
    private val lockName: String,
) : IndexedDbByteStore {
    private var activeLeaseOwnerId: String? = null

    override suspend fun <T> transaction(
        storeNames: Set<String>,
        mode: IndexedDbTransactionMode,
        block: suspend (IndexedDbByteStore) -> T,
    ): T =
        if (mode == IndexedDbTransactionMode.READWRITE) {
            withBrowserWriteLock(database, databaseName, lockName) { leaseOwnerId ->
                activeLeaseOwnerId = leaseOwnerId
                try {
                    block(this)
                } finally {
                    activeLeaseOwnerId = null
                }
            }
        } else {
            block(this)
        }

    override suspend fun get(storeName: String, key: ByteArray): ByteArray? {
        val transaction = database.transaction(arrayOf(storeName), "readonly")
        val request = transaction.objectStore(storeName).get(key.toIndexedDbKey())
        val result = awaitNullableResult(request) ?: return null
        return toByteArrayValue(result)
    }

    override suspend fun put(storeName: String, key: ByteArray, value: ByteArray) {
        activeLeaseOwnerId?.let { ownerId ->
            writeBatchWithLease(
                listOf(IndexedDbWriteOperation.Put(storeName, key, value)),
                ownerId,
            )
            return
        }

        val transaction = database.transaction(arrayOf(storeName), "readwrite")
        val completion = observeCompletion(transaction)
        awaitResult(transaction.objectStore(storeName).put(value.toIndexedDbValue(), key.toIndexedDbKey()))
        completion.await()
    }

    override suspend fun delete(storeName: String, key: ByteArray) {
        activeLeaseOwnerId?.let { ownerId ->
            writeBatchWithLease(
                listOf(IndexedDbWriteOperation.Delete(storeName, key)),
                ownerId,
            )
            return
        }

        val transaction = database.transaction(arrayOf(storeName), "readwrite")
        val completion = observeCompletion(transaction)
        awaitResult(transaction.objectStore(storeName).delete(key.toIndexedDbKey()))
        completion.await()
    }

    override suspend fun writeBatch(operations: List<IndexedDbWriteOperation>) {
        if (operations.isEmpty()) return

        activeLeaseOwnerId?.let { ownerId ->
            writeBatchWithLease(operations, ownerId)
            return
        }

        val transaction = database.transaction(operations.map { it.storeName }.distinct().sorted().toTypedArray(), "readwrite")
        val completion = observeCompletion(transaction)
        try {
            for (operation in operations) {
                val store = transaction.objectStore(operation.storeName)
                when (operation) {
                    is IndexedDbWriteOperation.Delete ->
                        store.delete(operation.key.toIndexedDbKey())
                    is IndexedDbWriteOperation.Put ->
                        store.put(operation.value.toIndexedDbValue(), operation.key.toIndexedDbKey())
                }
            }
        } catch (cause: Throwable) {
            abortTransaction(transaction)
            throw cause
        }
        completion.await()
    }

    private suspend fun writeBatchWithLease(
        operations: List<IndexedDbWriteOperation>,
        ownerId: String,
    ) {
        val storeNames = (operations.map { it.storeName } + writeLeaseStoreName)
            .distinct()
            .sorted()
            .toTypedArray()
        val transaction = database.transaction(storeNames, "readwrite")
        val completion = observeCompletion(transaction)
        val request = transaction.objectStore(writeLeaseStoreName).get(lockName)

        request.onsuccess = { _: dynamic ->
            val lease = request.result
            if (
                lease == null ||
                lease.ownerId?.unsafeCast<String>() != ownerId ||
                lease.expiresAt.unsafeCast<Double>() <= currentTimeMillis()
            ) {
                completion.completeExceptionally(
                    IllegalStateException("Lost IndexedDB write lease for $databaseName")
                )
                abortTransaction(transaction)
            } else {
                try {
                    queueWriteOperations(transaction, operations)
                } catch (cause: Throwable) {
                    completion.completeExceptionally(cause)
                    abortTransaction(transaction)
                }
            }
        }
        request.onerror = { _: dynamic ->
            completion.completeExceptionally(
                IllegalStateException(request.error?.message?.unsafeCast<String>() ?: "IndexedDB write lease check failed")
            )
            abortTransaction(transaction)
        }

        completion.await()
    }

    private fun queueWriteOperations(
        transaction: dynamic,
        operations: List<IndexedDbWriteOperation>,
    ) {
        for (operation in operations) {
            val store = transaction.objectStore(operation.storeName)
            when (operation) {
                is IndexedDbWriteOperation.Delete ->
                    store.delete(operation.key.toIndexedDbKey())
                is IndexedDbWriteOperation.Put ->
                    store.put(operation.value.toIndexedDbValue(), operation.key.toIndexedDbKey())
            }
        }
    }

    override suspend fun scan(
        storeName: String,
        startKey: ByteArray?,
        includeStart: Boolean,
        endKey: ByteArray?,
        includeEnd: Boolean,
        reverse: Boolean,
        limit: UInt,
    ): List<Pair<ByteArray, ByteArray>> {
        if (limit == 0u) return emptyList()

        if (startKey != null && endKey != null) {
            val comparison = startKey.compareIndexedDbKey(endKey)
            if (comparison > 0 || (comparison == 0 && (!includeStart || !includeEnd))) {
                return emptyList()
            }
        }

        val direction = if (reverse) "prev" else "next"
        val transaction = database.transaction(arrayOf(storeName), "readonly")
        val store = transaction.objectStore(storeName)
        val range = createRange(startKey, includeStart, endKey, includeEnd)
        val request = if (range == null) {
            store.openCursor(undefined, direction)
        } else {
            store.openCursor(range, direction)
        }

        return awaitCursorScan(request, limit)
    }

    private suspend fun awaitCursorScan(
        request: dynamic,
        limit: UInt,
    ): List<Pair<ByteArray, ByteArray>> = suspendCancellableCoroutine { continuation ->
        val values = mutableListOf<Pair<ByteArray, ByteArray>>()
        var completed = false

        request.onsuccess = { _: dynamic ->
            if (!completed) {
                val cursor = request.result
                if (cursor == null) {
                    completed = true
                    continuation.resume(values)
                } else {
                    values.add(toByteArrayKey(cursor.key) to toByteArrayValue(cursor.value))
                    if (values.size.toUInt() >= limit) {
                        completed = true
                        continuation.resume(values)
                    } else {
                        cursor.unsafeCast<IndexedDbCursor>().continueCursor()
                    }
                }
            }
        }
        request.onerror = { _: dynamic ->
            if (!completed) {
                completed = true
                continuation.resumeWithException(
                    IllegalStateException(request.error?.message?.unsafeCast<String>() ?: "IndexedDB cursor request failed")
                )
            }
        }
    }

    override suspend fun close() {
        database.close()
    }
}

private fun createRange(
    startKey: ByteArray?,
    includeStart: Boolean,
    endKey: ByteArray?,
    includeEnd: Boolean,
): dynamic = when {
    startKey != null && endKey != null ->
        idbKeyRange.bound(startKey.toIndexedDbKey(), endKey.toIndexedDbKey(), !includeStart, !includeEnd)
    startKey != null ->
        idbKeyRange.lowerBound(startKey.toIndexedDbKey(), !includeStart)
    endKey != null ->
        idbKeyRange.upperBound(endKey.toIndexedDbKey(), !includeEnd)
    else -> null
}

private fun ByteArray.toIndexedDbKey(): Array<Int> = Array(size) { index ->
    this[index].toInt() and 0xFF
}

private fun ByteArray.compareIndexedDbKey(other: ByteArray): Int {
    val sizeToCompare = minOf(size, other.size)
    for (index in 0 until sizeToCompare) {
        val left = this[index].toInt() and 0xFF
        val right = other[index].toInt() and 0xFF
        if (left != right) return left - right
    }
    return size - other.size
}

private fun ByteArray.toIndexedDbValue(): dynamic {
    val byteLength = size
    val array = js("new Int8Array(byteLength)")
    for (index in indices) {
        array[index] = this[index]
    }
    return array
}

private fun toByteArrayKey(value: dynamic): ByteArray {
    return toByteArrayFromIndexed(value)
}

private fun toByteArrayValue(value: dynamic): ByteArray {
    return toByteArrayFromIndexed(value)
}

private fun toByteArrayFromIndexed(value: dynamic): ByteArray {
    val size = value.length.unsafeCast<Int>()
    return ByteArray(size) { index ->
        value[index].unsafeCast<Int>().toByte()
    }
}

private suspend fun awaitResult(request: dynamic): dynamic = suspendCancellableCoroutine { continuation ->
    request.onsuccess = { _: dynamic -> continuation.resume(request.result) }
    request.onerror = { _: dynamic ->
        continuation.resumeWithException(IllegalStateException(request.error?.message?.unsafeCast<String>() ?: "IndexedDB request failed"))
    }
}

private suspend fun awaitOpenResult(request: dynamic): dynamic = suspendCancellableCoroutine { continuation ->
    var completed = false
    request.onsuccess = { _: dynamic ->
        if (!completed) {
            completed = true
            continuation.resume(request.result)
        }
    }
    request.onerror = { _: dynamic ->
        if (!completed) {
            completed = true
            continuation.resumeWithException(IllegalStateException(request.error?.message?.unsafeCast<String>() ?: "IndexedDB open failed"))
        }
    }
    request.onblocked = { _: dynamic ->
        if (!completed) {
            completed = true
            continuation.resumeWithException(IllegalStateException("IndexedDB open blocked by another connection"))
        }
    }
}

private suspend fun awaitNullableResult(request: dynamic): dynamic = suspendCancellableCoroutine { continuation ->
    request.onsuccess = { _: dynamic -> continuation.resume(request.result) }
    request.onerror = { _: dynamic ->
        continuation.resumeWithException(IllegalStateException(request.error?.message?.unsafeCast<String>() ?: "IndexedDB request failed"))
    }
}

private fun observeCompletion(transaction: dynamic): CompletableDeferred<Unit> {
    val completion = CompletableDeferred<Unit>()
    transaction.oncomplete = { _: dynamic -> completion.complete(Unit) }
    transaction.onerror = { _: dynamic ->
        completion.completeExceptionally(
            IllegalStateException(transaction.error?.message?.unsafeCast<String>() ?: "IndexedDB transaction failed")
        )
    }
    transaction.onabort = { _: dynamic ->
        completion.completeExceptionally(
            IllegalStateException(transaction.error?.message?.unsafeCast<String>() ?: "IndexedDB transaction aborted")
        )
    }
    return completion
}

private fun abortTransaction(transaction: dynamic) {
    try {
        transaction.abort()
    } catch (_: Throwable) {
        // Transaction may already be inactive after a synchronous IndexedDB failure.
    }
}

private suspend fun <T> withBrowserWriteLock(
    database: dynamic,
    databaseName: String,
    lockName: String,
    block: suspend (leaseOwnerId: String?) -> T,
): T {
    if (!hasWebLocks()) {
        return withIndexedDbWriteLease(database, databaseName, lockName, block)
    }

    return suspendCancellableCoroutine { continuation ->
        requestWebLock(
            lockName = lockName,
            onAcquired = { release ->
                if (!continuation.isActive) {
                    releaseWebLock(release)
                    return@requestWebLock
                }
                val blockJob = CoroutineScope(continuation.context).launch {
                    try {
                        continuation.resume(block(null))
                    } catch (cause: Throwable) {
                        continuation.resumeWithException(cause)
                    } finally {
                        releaseWebLock(release)
                    }
                }
                continuation.invokeOnCancellation { blockJob.cancel() }
            },
            onError = {
                if (!continuation.isActive) return@requestWebLock
                val fallbackJob = CoroutineScope(continuation.context).launch {
                    try {
                        continuation.resume(withIndexedDbWriteLease(database, databaseName, lockName, block))
                    } catch (cause: Throwable) {
                        continuation.resumeWithException(cause)
                    }
                }
                continuation.invokeOnCancellation { fallbackJob.cancel() }
            },
        )
    }
}

private suspend fun <T> withIndexedDbWriteLease(
    database: dynamic,
    databaseName: String,
    lockName: String,
    block: suspend (leaseOwnerId: String) -> T,
): T {
    val ownerId = createLeaseOwnerId()
    while (!tryAcquireWriteLease(database, lockName, ownerId)) {
        awaitWriteLeaseSignal(databaseName)
    }

    return coroutineScope {
        var leaseFailure: Throwable? = null
        val renewal = launch {
            while (isActive) {
                delay(writeLeaseRenewIntervalMillis)
                if (!renewWriteLease(database, lockName, ownerId)) {
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
                releaseWriteLease(database, lockName, ownerId)
                signalWriteLeaseRelease(databaseName)
            }
        }
    }
}

private suspend fun tryAcquireWriteLease(
    database: dynamic,
    lockName: String,
    ownerId: String,
): Boolean = awaitLeaseTransaction(
    start = { onComplete, onError, onCancel ->
        acquireOrRenewWriteLease(database, lockName, ownerId, true, onComplete, onError, onCancel)
    },
    onLateAcquire = {
        try {
            releaseIndexedDbWriteLease(database, lockName, ownerId, {}, {})
        } catch (_: Throwable) {
            // Best effort: cancellation may close the database concurrently.
        }
    },
)

private suspend fun renewWriteLease(
    database: dynamic,
    lockName: String,
    ownerId: String,
): Boolean = awaitLeaseTransaction(
    start = { onComplete, onError, onCancel ->
        acquireOrRenewWriteLease(database, lockName, ownerId, false, onComplete, onError, onCancel)
    },
)

private suspend fun awaitLeaseTransaction(
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

private fun acquireOrRenewWriteLease(
    database: dynamic,
    lockName: String,
    ownerId: String,
    allowExpiredOwner: Boolean,
    onComplete: (Boolean) -> Unit,
    onError: (String) -> Unit,
    onCancel: (() -> Unit) -> Unit,
) {
    // Raw JS blocks can capture function locals, but not top-level Kotlin constants.
    // Keep the store name in a local so generated JS does not resolve an undefined
    // `writeLeaseStoreName` global when the Web Locks fallback is used.
    val leaseStoreName = writeLeaseStoreName
    val leaseDurationMillis = writeLeaseDurationMillis
    val handoffHook = indexedDbLeaseAcquisitionHandoffHook
    js(
        """
        let completed = false;
        let acquired = false;
        const transaction = database.transaction([leaseStoreName], "readwrite");
        const store = transaction.objectStore(leaseStoreName);
        const request = store.get(lockName);
        request.onsuccess = () => {
            const now = Date.now();
            const current = request.result;
            if (
                current === undefined ||
                current.ownerId === ownerId ||
                (allowExpiredOwner && current.expiresAt <= now)
            ) {
                acquired = true;
                store.put({ ownerId, expiresAt: now + leaseDurationMillis }, lockName);
            }
        };
        const fail = (message) => {
            if (!completed) {
                completed = true;
                onError(message);
            }
        };
        transaction.oncomplete = () => {
            if (!completed) {
                completed = true;
                if (acquired && handoffHook) handoffHook();
                onComplete(acquired);
            }
        };
        transaction.onerror = () => fail(
            transaction.error && transaction.error.message
                ? transaction.error.message
                : "IndexedDB write lease transaction failed"
        );
        transaction.onabort = () => fail(
            transaction.error && transaction.error.message
                ? transaction.error.message
                : "IndexedDB write lease transaction aborted"
        );
        onCancel(() => {
            completed = true;
            try {
                transaction.abort();
            } catch (_) {
            }
        });
        """
    )
}

private suspend fun releaseWriteLease(
    database: dynamic,
    lockName: String,
    ownerId: String,
): Unit = suspendCancellableCoroutine { continuation ->
    releaseIndexedDbWriteLease(
        database,
        lockName,
        ownerId,
        { if (continuation.isActive) continuation.resume(Unit) },
        { message ->
            if (continuation.isActive) {
                continuation.resumeWithException(IllegalStateException(message))
            }
        },
    )
}

private fun releaseIndexedDbWriteLease(
    database: dynamic,
    lockName: String,
    ownerId: String,
    onComplete: () -> Unit,
    onError: (String) -> Unit,
) {
    // See acquireOrRenewWriteLease: raw JS may only reference function locals.
    val leaseStoreName = writeLeaseStoreName
    js(
        """
        let completed = false;
        const transaction = database.transaction([leaseStoreName], "readwrite");
        const store = transaction.objectStore(leaseStoreName);
        const request = store.get(lockName);
        request.onsuccess = () => {
            if (request.result && request.result.ownerId === ownerId) {
                store.delete(lockName);
            }
        };
        const fail = (message) => {
            if (!completed) {
                completed = true;
                onError(message);
            }
        };
        transaction.oncomplete = () => {
            if (!completed) {
                completed = true;
                onComplete();
            }
        };
        transaction.onerror = () => fail(
            transaction.error && transaction.error.message
                ? transaction.error.message
                : "IndexedDB write lease release failed"
        );
        transaction.onabort = () => fail(
            transaction.error && transaction.error.message
                ? transaction.error.message
                : "IndexedDB write lease release aborted"
        );
        """
    )
}

private suspend fun awaitWriteLeaseSignal(databaseName: String): Unit =
    suspendCancellableCoroutine { continuation ->
        waitForWriteLeaseSignal(
            writeLeaseChannelName(databaseName),
            writeLeaseRetryMillis,
        ) {
            if (continuation.isActive) continuation.resume(Unit)
        }
    }

private fun waitForWriteLeaseSignal(
    channelName: String,
    timeoutMillis: Int,
    onComplete: () -> Unit,
) {
    js(
        """
        let completed = false;
        const channel = typeof BroadcastChannel === "function"
            ? new BroadcastChannel(channelName)
            : null;
        const finish = () => {
            if (!completed) {
                completed = true;
                if (channel) channel.close();
                onComplete();
            }
        };
        if (channel) channel.onmessage = finish;
        setTimeout(finish, timeoutMillis);
        """
    )
}

private fun signalWriteLeaseRelease(databaseName: String) {
    val channelName = writeLeaseChannelName(databaseName)
    js(
        """
        if (typeof BroadcastChannel === "function") {
            const channel = new BroadcastChannel(channelName);
            channel.postMessage("released");
            channel.close();
        }
        """
    )
}

private fun createLeaseOwnerId(): String =
    js("Date.now().toString(36) + '-' + Math.random().toString(36).slice(2)").unsafeCast<String>()

private fun currentTimeMillis(): Double =
    js("Date.now()").unsafeCast<Double>()

private fun writeLeaseChannelName(databaseName: String): String =
    "maryk-indexeddb-lease:$databaseName"

private const val writeLeaseStoreName = "__maryk_write_lease"
private const val writeLeaseDurationMillis = 30_000
private const val writeLeaseRenewIntervalMillis = 10_000L
private const val writeLeaseRetryMillis = 100

internal var indexedDbLeaseAcquisitionHandoffHook: (() -> Unit)? = null

private fun hasWebLocks(): Boolean =
    js(
        """
        (function() {
            try {
                return typeof navigator !== "undefined" &&
                    !!navigator.locks &&
                    typeof navigator.locks.request === "function";
            } catch (_) {
                return false;
            }
        })()
        """
    ).unsafeCast<Boolean>()

private fun requestWebLock(
    lockName: String,
    onAcquired: (dynamic) -> Unit,
    onError: (String) -> Unit,
) {
    js(
        """
        try {
            const locks = navigator.locks;
            if (!locks || typeof locks.request !== "function") {
                throw new Error("Web Locks API is unavailable");
            }
            locks.request(lockName, { mode: "exclusive" }, () => new Promise((resolve) => {
                onAcquired(resolve);
            })).catch((error) => {
                onError(error && error.message ? error.message : "Could not acquire IndexedDB write lock");
            });
        } catch (error) {
            onError(error && error.message ? error.message : "Could not acquire IndexedDB write lock");
        }
        """
    )
}

private fun releaseWebLock(release: dynamic) {
    release()
}

private external val indexedDB: dynamic

private external val IDBKeyRange: dynamic

private val idbKeyRange get() = IDBKeyRange

private external interface IndexedDbCursor {
    @JsName("continue")
    fun continueCursor()
}
