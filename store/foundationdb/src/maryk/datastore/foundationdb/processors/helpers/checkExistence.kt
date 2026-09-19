package maryk.datastore.foundationdb.processors.helpers

import maryk.core.exceptions.StorageException

/** Check existence of the [prefixWithKeyRange] on [iterator] by checking existence of creation time */
internal fun checkExistence(
    iterator: FDBIterator,
    prefixWithKeyRange: ByteArray
) {
    if (!iterator.hasNext() || !iterator.next().key.contentEquals(prefixWithKeyRange)) {
        throw StorageException("Key does not exist while it should have existed")
    }
}
