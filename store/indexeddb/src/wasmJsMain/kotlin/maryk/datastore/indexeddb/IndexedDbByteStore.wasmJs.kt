@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package maryk.datastore.indexeddb

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    val storeNames = JsArray<JsString>().also { array ->
        objectStoreNames.sorted().forEachIndexed { index, storeName ->
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

    return WasmIndexedDbByteStore(database, "maryk-indexeddb:$databaseName")
}

private class WasmIndexedDbByteStore(
    private val database: JsAny,
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
            transaction = database.transaction(storeNames, "readwrite");
            transaction.oncomplete = () => succeed();
            transaction.onerror = () => fail(transaction.error?.message ?? "IndexedDB transaction failed");
            transaction.onabort = () => fail(transaction.error?.message ?? "IndexedDB transaction aborted");
            for (let index = 0; index < operations.length; index++) {
                const operation = operations[index];
                const store = transaction.objectStore(operation.storeName);
                if (operation.type === "put") {
                    store.put(operation.value, operation.key);
                } else {
                    store.delete(operation.key);
                }
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
