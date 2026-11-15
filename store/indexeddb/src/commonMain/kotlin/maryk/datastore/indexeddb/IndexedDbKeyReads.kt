package maryk.datastore.indexeddb

/** Native key enumeration avoids cloning retained journal payloads merely to prune their keys. */
internal interface IndexedDbKeyReadStore {
    suspend fun getAllKeys(storeName: String): List<ByteArray>
}

internal suspend fun IndexedDbByteStore.getAllKeys(storeName: String): List<ByteArray> =
    if (this is IndexedDbKeyReadStore) getAllKeys(storeName) else scan(storeName).map { it.first }
