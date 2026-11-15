package maryk.datastore.indexeddb

/** Optional native capability; custom adapters retain the ordered get fallback. */
internal interface IndexedDbBatchReadStore {
    suspend fun getBatchValues(storeName: String, keys: List<ByteArray>): List<ByteArray?>

    /** Native adapters can release raw results before retaining the rest of a batch. */
    suspend fun <T> mapBatchValues(storeName: String, keys: List<ByteArray>, transform: (Int, ByteArray?) -> T): List<T> =
        getBatchValues(storeName, keys).mapIndexed(transform)
}

/** Queue a bounded group of reads together while preserving duplicates and missing keys. */
internal suspend fun IndexedDbByteStore.getBatch(storeName: String, keys: List<ByteArray>): List<ByteArray?> =
    if (keys.isEmpty()) emptyList()
    else if (this is IndexedDbBatchReadStore) {
        if (keys.size <= 256) getBatchValues(storeName, keys)
        else buildList(keys.size) {
            for (batch in keys.asSequence().chunked(256)) {
                addAll(getBatchValues(storeName, batch))
            }
        }
    }
    else keys.map { get(storeName, it) }
