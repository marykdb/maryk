@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package maryk.datastore.indexeddb

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
import kotlin.js.JsAny
import kotlin.js.JsArray
import kotlin.js.JsNumber
import kotlin.js.JsString
import kotlin.js.js
import kotlin.js.toInt
import kotlin.js.toJsNumber
import kotlin.js.toJsString
import kotlin.js.unsafeCast

internal actual suspend fun openPlatformIndexedDbByteStore(
    databaseName: String,
    objectStoreNames: Set<String>,
    version: Int,
): IndexedDbByteStore {
    val requiredObjectStoreNames = objectStoreNames + writeLeaseStoreName
    val storeNames = JsArray<JsString>().also { array ->
        requiredObjectStoreNames.sorted().forEachIndexed { index, storeName ->
            array[index] = storeName.toJsString()
        }
    }

    val database = suspendCancellableCoroutine { continuation ->
        openIndexedDb(
            databaseName = databaseName,
            storeNames = storeNames,
            version = version,
            onSuccess = { continuation.resume(it) },
            onError = { continuation.resumeWithException(IllegalStateException(it)) },
        )
    }

    return WasmIndexedDbByteStore(database, databaseName, "maryk-indexeddb:$databaseName")
}

private class WasmIndexedDbByteStore(
    private val database: JsAny,
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
        val result = suspendCancellableCoroutine<JsAny?> { continuation ->
            getIndexedDbValue(
                database = database,
                storeName = storeName,
                key = key.toIndexedDbKey(),
                onSuccess = { continuation.resume(it) },
                onError = { continuation.resumeWithException(IllegalStateException(it)) },
            )
        } ?: return null

        return result.toByteArrayFromIndexed()
    }

    override suspend fun put(storeName: String, key: ByteArray, value: ByteArray) {
        if (activeLeaseOwnerId != null) {
            writeBatch(listOf(IndexedDbWriteOperation.Put(storeName, key, value)))
            return
        }

        suspendCancellableCoroutine<Unit> { continuation ->
            putIndexedDbValue(
                database = database,
                storeName = storeName,
                key = key.toIndexedDbKey(),
                value = value.toIndexedDbValue(),
                onSuccess = { continuation.resume(Unit) },
                onError = { continuation.resumeWithException(IllegalStateException(it)) },
            )
        }
    }

    override suspend fun delete(storeName: String, key: ByteArray) {
        if (activeLeaseOwnerId != null) {
            writeBatch(listOf(IndexedDbWriteOperation.Delete(storeName, key)))
            return
        }

        suspendCancellableCoroutine<Unit> { continuation ->
            deleteIndexedDbValue(
                database = database,
                storeName = storeName,
                key = key.toIndexedDbKey(),
                onSuccess = { continuation.resume(Unit) },
                onError = { continuation.resumeWithException(IllegalStateException(it)) },
            )
        }
    }

    override suspend fun writeBatch(operations: List<IndexedDbWriteOperation>) {
        if (operations.isEmpty()) return

        val indexedOperations = JsArray<JsAny>().also { array ->
            operations.forEachIndexed { index, operation ->
                array[index] = when (operation) {
                    is IndexedDbWriteOperation.Delete -> createDeleteOperation(
                        storeName = operation.storeName,
                        key = operation.key.toIndexedDbKey(),
                    )
                    is IndexedDbWriteOperation.Put -> createPutOperation(
                        storeName = operation.storeName,
                        key = operation.key.toIndexedDbKey(),
                        value = operation.value.toIndexedDbValue(),
                    )
                }
            }
        }

        suspendCancellableCoroutine<Unit> { continuation ->
            writeIndexedDbBatch(
                database = database,
                operations = indexedOperations,
                leaseStoreName = writeLeaseStoreName,
                lockName = lockName,
                leaseOwnerId = activeLeaseOwnerId?.toJsString(),
                onSuccess = { continuation.resume(Unit) },
                onError = { continuation.resumeWithException(IllegalStateException(it)) },
            )
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
        if (startKey != null && endKey != null) {
            val comparison = startKey.compareIndexedDbKey(endKey)
            if (comparison > 0 || (comparison == 0 && (!includeStart || !includeEnd))) {
                return emptyList()
            }
        }

        val rows = suspendCancellableCoroutine<JsArray<JsAny>> { continuation ->
            scanIndexedDbValues(
                database = database,
                storeName = storeName,
                startKey = startKey?.toIndexedDbKey(),
                includeStart = includeStart,
                endKey = endKey?.toIndexedDbKey(),
                includeEnd = includeEnd,
                reverse = reverse,
                limit = limit.toIndexedDbLimit(),
                onSuccess = { continuation.resume(it) },
                onError = { continuation.resumeWithException(IllegalStateException(it)) },
            )
        }

        return rows.toPairList()
    }

    override suspend fun close() {
        closeIndexedDb(database)
    }
}

private fun ByteArray.toIndexedDbKey(): JsArray<JsNumber> =
    JsArray<JsNumber>().also { array ->
        forEachIndexed { index, byte ->
            array[index] = (byte.toInt() and 0xFF).toJsNumber()
        }
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

private fun ByteArray.toIndexedDbValue(): JsArray<JsNumber> =
    JsArray<JsNumber>().also { array ->
        forEachIndexed { index, byte ->
            array[index] = byte.toInt().toJsNumber()
        }
    }

private fun JsAny.toByteArrayFromIndexed(): ByteArray {
    val array = unsafeCast<JsArray<JsNumber>>()
    return ByteArray(array.length) { index -> array[index]!!.toInt().toByte() }
}

private fun UInt.toIndexedDbLimit(): Int =
    if (this > Int.MAX_VALUE.toUInt()) Int.MAX_VALUE else toInt()

private fun JsArray<JsAny>.toPairList(): List<Pair<ByteArray, ByteArray>> =
    toList().map { row ->
        val rowArray = row.unsafeCast<JsArray<JsAny>>()
        rowArray[0]!!.toByteArrayFromIndexed() to rowArray[1]!!.toByteArrayFromIndexed()
    }

private fun openIndexedDb(
    databaseName: String,
    storeNames: JsArray<JsString>,
    version: Int,
    onSuccess: (JsAny) -> Unit,
    onError: (String) -> Unit,
) {
    js(
        """
        const createMissingStores = (db) => {
            for (let index = 0; index < storeNames.length; index++) {
                const storeName = storeNames[index];
                if (!db.objectStoreNames.contains(storeName)) {
                    db.createObjectStore(storeName);
                }
            }
        };

        const missingStores = (db) => {
            const missing = [];
            for (let index = 0; index < storeNames.length; index++) {
                const storeName = storeNames[index];
                if (!db.objectStoreNames.contains(storeName)) {
                    missing.push(storeName);
                }
            }
            return missing;
        };

        let finished = false;
        const finishSuccess = (db) => {
            if (!finished) {
                finished = true;
                onSuccess(db);
            }
        };
        const finishError = (message) => {
            if (!finished) {
                finished = true;
                onError(message);
            }
        };

        const startOpen = (requestedVersion) => {
            const request = requestedVersion === 1
                ? globalThis.indexedDB.open(databaseName)
                : globalThis.indexedDB.open(databaseName, requestedVersion);
            request.onupgradeneeded = () => createMissingStores(request.result);
            request.onsuccess = () => {
                const db = request.result;
                if (missingStores(db).length > 0) {
                    const nextVersion = db.version + 1;
                    db.close();
                    startOpen(nextVersion);
                    return;
                }
                db.onversionchange = () => db.close();
                finishSuccess(db);
            };
            request.onerror = () => finishError(request.error?.message ?? "IndexedDB open failed");
            request.onblocked = () => finishError("IndexedDB open blocked by another connection");
        };

        startOpen(version);
        """
    )
}

private fun createPutOperation(
    storeName: String,
    key: JsArray<JsNumber>,
    value: JsArray<JsNumber>,
): JsAny =
    js("({ type: 'put', storeName, key, value })")

private fun createDeleteOperation(
    storeName: String,
    key: JsArray<JsNumber>,
): JsAny =
    js("({ type: 'delete', storeName, key })")

private fun writeIndexedDbBatch(
    database: JsAny,
    operations: JsArray<JsAny>,
    leaseStoreName: String,
    lockName: String,
    leaseOwnerId: JsString?,
    onSuccess: () -> Unit,
    onError: (String) -> Unit,
) {
    js(
        """
        let transaction;
        let done = false;
        const succeed = () => {
            if (!done) {
                done = true;
                onSuccess();
            }
        };
        const fail = (message) => {
            if (!done) {
                done = true;
                onError(message);
            }
        };
        try {
            const storeNames = [...new Set(Array.from(operations, operation => operation.storeName))].sort();
            if (leaseOwnerId !== null) {
                storeNames.push(leaseStoreName);
                storeNames.sort();
            }
            transaction = database.transaction(storeNames, "readwrite");
            transaction.oncomplete = () => succeed();
            transaction.onerror = () => fail(transaction.error?.message ?? "IndexedDB transaction failed");
            transaction.onabort = () => fail(transaction.error?.message ?? "IndexedDB transaction aborted");

            const queueOperations = () => {
                for (let index = 0; index < operations.length; index++) {
                    const operation = operations[index];
                    const store = transaction.objectStore(operation.storeName);
                    if (operation.type === "put") {
                        store.put(operation.value, operation.key);
                    } else {
                        store.delete(operation.key);
                    }
                }
            };

            if (leaseOwnerId === null) {
                queueOperations();
            } else {
                const leaseRequest = transaction.objectStore(leaseStoreName).get(lockName);
                leaseRequest.onsuccess = () => {
                    const lease = leaseRequest.result;
                    if (!lease || lease.ownerId !== leaseOwnerId || lease.expiresAt <= Date.now()) {
                        fail("Lost IndexedDB write lease");
                        transaction.abort();
                    } else {
                        queueOperations();
                    }
                };
                leaseRequest.onerror = () => {
                    fail(leaseRequest.error?.message ?? "IndexedDB write lease check failed");
                    transaction.abort();
                };
            }
        } catch (error) {
            try {
                transaction?.abort();
            } catch (_) {
            }
            fail(error?.message ?? "IndexedDB batch failed");
        }
        """
    )
}

private fun getIndexedDbValue(
    database: JsAny,
    storeName: String,
    key: JsArray<JsNumber>,
    onSuccess: (JsAny?) -> Unit,
    onError: (String) -> Unit,
) {
    js(
        """
        let done = false;
        const succeed = (value) => {
            if (!done) {
                done = true;
                onSuccess(value);
            }
        };
        const fail = (message) => {
            if (!done) {
                done = true;
                onError(message);
            }
        };
        const transaction = database.transaction([storeName], "readonly");
        const request = transaction.objectStore(storeName).get(key);
        request.onsuccess = () => succeed(request.result ?? null);
        request.onerror = () => fail(request.error?.message ?? "IndexedDB get failed");
        transaction.onerror = () => fail(transaction.error?.message ?? "IndexedDB transaction failed");
        transaction.onabort = () => fail(transaction.error?.message ?? "IndexedDB transaction aborted");
        """
    )
}

private fun putIndexedDbValue(
    database: JsAny,
    storeName: String,
    key: JsArray<JsNumber>,
    value: JsArray<JsNumber>,
    onSuccess: () -> Unit,
    onError: (String) -> Unit,
) {
    js(
        """
        let done = false;
        const succeed = () => {
            if (!done) {
                done = true;
                onSuccess();
            }
        };
        const fail = (message) => {
            if (!done) {
                done = true;
                onError(message);
            }
        };
        const transaction = database.transaction([storeName], "readwrite");
        const request = transaction.objectStore(storeName).put(value, key);
        request.onerror = () => fail(request.error?.message ?? "IndexedDB put failed");
        transaction.oncomplete = () => succeed();
        transaction.onerror = () => fail(transaction.error?.message ?? "IndexedDB transaction failed");
        transaction.onabort = () => fail(transaction.error?.message ?? "IndexedDB transaction aborted");
        """
    )
}

private fun deleteIndexedDbValue(
    database: JsAny,
    storeName: String,
    key: JsArray<JsNumber>,
    onSuccess: () -> Unit,
    onError: (String) -> Unit,
) {
    js(
        """
        let done = false;
        const succeed = () => {
            if (!done) {
                done = true;
                onSuccess();
            }
        };
        const fail = (message) => {
            if (!done) {
                done = true;
                onError(message);
            }
        };
        const transaction = database.transaction([storeName], "readwrite");
        const request = transaction.objectStore(storeName).delete(key);
        request.onerror = () => fail(request.error?.message ?? "IndexedDB delete failed");
        transaction.oncomplete = () => succeed();
        transaction.onerror = () => fail(transaction.error?.message ?? "IndexedDB transaction failed");
        transaction.onabort = () => fail(transaction.error?.message ?? "IndexedDB transaction aborted");
        """
    )
}

private fun scanIndexedDbValues(
    database: JsAny,
    storeName: String,
    startKey: JsArray<JsNumber>?,
    includeStart: Boolean,
    endKey: JsArray<JsNumber>?,
    includeEnd: Boolean,
    reverse: Boolean,
    limit: Int,
    onSuccess: (JsArray<JsAny>) -> Unit,
    onError: (String) -> Unit,
) {
    js(
        """
        let done = false;
        const succeed = (rows) => {
            if (!done) {
                done = true;
                onSuccess(rows);
            }
        };
        const fail = (message) => {
            if (!done) {
                done = true;
                onError(message);
            }
        };
        const transaction = database.transaction([storeName], "readonly");
        const store = transaction.objectStore(storeName);
        let range = null;
        if (startKey !== null && endKey !== null) {
            range = globalThis.IDBKeyRange.bound(startKey, endKey, !includeStart, !includeEnd);
        } else if (startKey !== null) {
            range = globalThis.IDBKeyRange.lowerBound(startKey, !includeStart);
        } else if (endKey !== null) {
            range = globalThis.IDBKeyRange.upperBound(endKey, !includeEnd);
        }

        const rows = [];
        const request = store.openCursor(range === null ? undefined : range, reverse ? "prev" : "next");
        request.onsuccess = () => {
            const cursor = request.result;
            if (cursor && rows.length < limit) {
                rows.push([cursor.key, cursor.value]);
                cursor.continue();
            } else {
                succeed(rows);
            }
        };
        request.onerror = () => fail(request.error?.message ?? "IndexedDB cursor failed");
        transaction.onerror = () => fail(transaction.error?.message ?? "IndexedDB transaction failed");
        transaction.onabort = () => fail(transaction.error?.message ?? "IndexedDB transaction aborted");
        """
    )
}

private suspend fun <T> withBrowserWriteLock(
    database: JsAny,
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
            onError = { message ->
                continuation.resumeWithException(IllegalStateException(message))
            },
        )
    }
}

private suspend fun <T> withIndexedDbWriteLease(
    database: JsAny,
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
    database: JsAny,
    lockName: String,
    ownerId: String,
): Boolean = awaitLeaseTransaction { onComplete, onError ->
    acquireOrRenewWriteLease(
        database,
        writeLeaseStoreName,
        lockName,
        ownerId,
        true,
        writeLeaseDurationMillis,
        onComplete,
        onError,
    )
}

private suspend fun renewWriteLease(
    database: JsAny,
    lockName: String,
    ownerId: String,
): Boolean = awaitLeaseTransaction { onComplete, onError ->
    acquireOrRenewWriteLease(
        database,
        writeLeaseStoreName,
        lockName,
        ownerId,
        false,
        writeLeaseDurationMillis,
        onComplete,
        onError,
    )
}

private suspend fun awaitLeaseTransaction(
    start: ((Boolean) -> Unit, (String) -> Unit) -> Unit,
): Boolean = suspendCancellableCoroutine { continuation ->
    start(
        { acquired -> if (continuation.isActive) continuation.resume(acquired) },
        { message ->
            if (continuation.isActive) {
                continuation.resumeWithException(IllegalStateException(message))
            }
        },
    )
}

private fun acquireOrRenewWriteLease(
    database: JsAny,
    leaseStoreName: String,
    lockName: String,
    ownerId: String,
    allowExpiredOwner: Boolean,
    leaseDurationMillis: Int,
    onComplete: (Boolean) -> Unit,
    onError: (String) -> Unit,
): Unit {
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
                onComplete(acquired);
            }
        };
        transaction.onerror = () => fail(
            transaction.error?.message ?? "IndexedDB write lease transaction failed"
        );
        transaction.onabort = () => fail(
            transaction.error?.message ?? "IndexedDB write lease transaction aborted"
        );
        """
    )
}

private suspend fun releaseWriteLease(
    database: JsAny,
    lockName: String,
    ownerId: String,
): Unit = suspendCancellableCoroutine { continuation ->
    releaseIndexedDbWriteLease(
        database,
        writeLeaseStoreName,
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
    database: JsAny,
    leaseStoreName: String,
    lockName: String,
    ownerId: String,
    onComplete: () -> Unit,
    onError: (String) -> Unit,
): Unit {
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
            transaction.error?.message ?? "IndexedDB write lease release failed"
        );
        transaction.onabort = () => fail(
            transaction.error?.message ?? "IndexedDB write lease release aborted"
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

private fun signalWriteLeaseRelease(databaseName: String): Unit =
    postWriteLeaseRelease(writeLeaseChannelName(databaseName))

private fun postWriteLeaseRelease(channelName: String) {
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
    createLeaseOwnerIdJs().toString()

private fun createLeaseOwnerIdJs(): JsString =
    js("Date.now().toString(36) + '-' + Math.random().toString(36).slice(2)")

private fun writeLeaseChannelName(databaseName: String): String =
    "maryk-indexeddb-lease:$databaseName"

private const val writeLeaseStoreName = "__maryk_write_lease"
private const val writeLeaseDurationMillis = 30_000
private const val writeLeaseRenewIntervalMillis = 10_000L
private const val writeLeaseRetryMillis = 100

private fun hasWebLocks(): Boolean =
    js("!!(typeof navigator !== 'undefined' && navigator.locks && navigator.locks.request)")

private fun requestWebLock(
    lockName: String,
    onAcquired: (JsAny) -> Unit,
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

private fun releaseWebLock(release: JsAny) {
    js("release();")
}

private fun closeIndexedDb(database: JsAny) {
    js("database.close();")
}
