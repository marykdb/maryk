package maryk.datastore.rocksdb.processors.helpers

import maryk.core.properties.types.Key
import maryk.rocksdb.RocksIterator

/** Check existence of the [key] on [iterator] by checking existence of creation time */
fun checkExistence(
    iterator: RocksIterator,
    key: Key<*>
) {
    // Start at begin of record
    iterator.seek(key.bytes)
    if (!iterator.isValid()) {
        // Is already past key so key does not exist
        // Should not happen since this needs to be checked before
        throw Exception("Key does not exist while it should have existed")
    }

    val creationDateKey = iterator.key()
    if (!key.bytes.contentEquals(creationDateKey)) {
        // Is already past key so key does not exist
        // Should not happen since this needs to be checked before
        throw Exception("Key does not exist while it should have existed")
    }
}
