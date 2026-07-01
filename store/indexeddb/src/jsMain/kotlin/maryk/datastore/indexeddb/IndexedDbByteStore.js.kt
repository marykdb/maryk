@file:Suppress("UnsafeCastFromDynamic")

package maryk.datastore.indexeddb

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal actual suspend fun openPlatformIndexedDbByteStore(
    databaseName: String,
    objectStoreNames: Set<String>,
    version: Int,
): IndexedDbByteStore {
    var database = openDatabase(databaseName, objectStoreNames, version)
    val missingStoreNames = missingObjectStoreNames(database, objectStoreNames)
    if (missingStoreNames.isNotEmpty()) {
        val nextVersion = database.version.unsafeCast<Int>() + 1
        database.close()
        database = openDatabase(databaseName, objectStoreNames, nextVersion)
    }
    return BrowserIndexedDbByteStore(database, "maryk-indexeddb:$databaseName")
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
    private val lockName: String,
) : IndexedDbByteStore {
    override suspend fun <T> transaction(
        storeNames: Set<String>,
        mode: IndexedDbTransactionMode,
        block: suspend (IndexedDbByteStore) -> T,
    ): T =
        if (mode == IndexedDbTransactionMode.READWRITE) {
            withBrowserWriteLock(lockName) { block(this) }
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
        val transaction = database.transaction(arrayOf(storeName), "readwrite")
        val completion = observeCompletion(transaction)
        awaitResult(transaction.objectStore(storeName).put(value.toIndexedDbValue(), key.toIndexedDbKey()))
        completion.await()
    }

    override suspend fun delete(storeName: String, key: ByteArray) {
        val transaction = database.transaction(arrayOf(storeName), "readwrite")
        val completion = observeCompletion(transaction)
        awaitResult(transaction.objectStore(storeName).delete(key.toIndexedDbKey()))
        completion.await()
    }

    override suspend fun writeBatch(operations: List<IndexedDbWriteOperation>) {
        if (operations.isEmpty()) return

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
    lockName: String,
    block: suspend () -> T,
): T {
    if (!hasWebLocks()) {
        val lock = localIndexedDbWriteLocks.getOrPut(lockName) { Mutex() }
        return lock.withLock {
            block()
        }
    }

    return suspendCancellableCoroutine { continuation ->
        requestWebLock(
            lockName = lockName,
            onAcquired = { release ->
                if (!continuation.isActive) {
                    releaseWebLock(release)
                    return@requestWebLock
                }
                CoroutineScope(continuation.context).launch {
                    try {
                        continuation.resume(block())
                    } catch (cause: Throwable) {
                        continuation.resumeWithException(cause)
                    } finally {
                        releaseWebLock(release)
                    }
                }
            },
            onError = { message ->
                continuation.resumeWithException(IllegalStateException(message))
            },
        )
    }
}

private val localIndexedDbWriteLocks = mutableMapOf<String, Mutex>()

private fun hasWebLocks(): Boolean =
    js("!!(typeof navigator !== 'undefined' && navigator.locks && navigator.locks.request)").unsafeCast<Boolean>()

private fun requestWebLock(
    lockName: String,
    onAcquired: (dynamic) -> Unit,
    onError: (String) -> Unit,
) {
    js(
        """
        navigator.locks.request(lockName, { mode: "exclusive" }, () => new Promise((resolve) => {
            onAcquired(resolve);
        })).catch((error) => {
            onError(error && error.message ? error.message : "Could not acquire IndexedDB write lock");
        });
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
