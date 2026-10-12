package maryk.datastore.indexeddb

/** Optional native capability; custom adapters retain the ordered get fallback. */
internal interface IndexedDbBatchReadStore {
    suspend fun getBatchValues(storeName: String, keys: List<ByteArray>): List<ByteArray?>
}

/** Queue a bounded group of reads together while preserving duplicates and missing keys. */
internal suspend fun IndexedDbByteStore.getBatch(storeName: String, keys: List<ByteArray>): List<ByteArray?> =
    if (this is IndexedDbBatchReadStore) keys.chunked(256).flatMap { getBatchValues(storeName, it) }
    else keys.map { get(storeName, it) }
