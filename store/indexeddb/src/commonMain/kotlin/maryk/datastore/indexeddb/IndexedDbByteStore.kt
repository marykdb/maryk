package maryk.datastore.indexeddb

interface IndexedDbByteStore {
    /**
     * Logical request scope.
     *
     * IndexedDB native transactions are active only while requests are queued in the
     * same browser task. The common datastore processor suspends between reads,
     * scans, validation, and write planning, so JS/Wasm wrappers cannot safely keep
     * one native transaction open for this whole block. Platform stores can still
     * use this scope for browser-level write serialization, then use native
     * transactions for individual reads/scans and atomic write batches.
     */
    suspend fun <T> transaction(
        storeNames: Set<String>,
        mode: IndexedDbTransactionMode,
        block: suspend (IndexedDbByteStore) -> T,
    ): T = block(this)

    suspend fun get(storeName: String, key: ByteArray): ByteArray?

    suspend fun put(storeName: String, key: ByteArray, value: ByteArray)

    suspend fun delete(storeName: String, key: ByteArray)

    /** Commit all queued writes in one native IndexedDB readwrite transaction where supported. */
    suspend fun writeBatch(operations: List<IndexedDbWriteOperation>) {
        for (operation in operations) {
            when (operation) {
                is IndexedDbWriteOperation.Delete -> delete(operation.storeName, operation.key)
                is IndexedDbWriteOperation.Put -> put(operation.storeName, operation.key, operation.value)
            }
        }
    }

    suspend fun scan(
        storeName: String,
        startKey: ByteArray? = null,
        includeStart: Boolean = true,
        endKey: ByteArray? = null,
        includeEnd: Boolean = true,
        reverse: Boolean = false,
        limit: UInt = UInt.MAX_VALUE,
    ): List<Pair<ByteArray, ByteArray>>

    suspend fun close()
}

enum class IndexedDbTransactionMode {
    READONLY,
    READWRITE,
}

sealed interface IndexedDbWriteOperation {
    val storeName: String
    val key: ByteArray

    data class Put(
        override val storeName: String,
        override val key: ByteArray,
        val value: ByteArray,
    ) : IndexedDbWriteOperation

    data class Delete(
        override val storeName: String,
        override val key: ByteArray,
    ) : IndexedDbWriteOperation
}

suspend fun openIndexedDbByteStore(
    databaseName: String,
    objectStoreNames: Set<String>,
    version: Int = 1,
): IndexedDbByteStore = openPlatformIndexedDbByteStore(databaseName, objectStoreNames, version)

internal expect suspend fun openPlatformIndexedDbByteStore(
    databaseName: String,
    objectStoreNames: Set<String>,
    version: Int,
): IndexedDbByteStore
