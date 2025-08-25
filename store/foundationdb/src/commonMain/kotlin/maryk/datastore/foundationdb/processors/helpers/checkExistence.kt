package maryk.datastore.foundationdb.processors.helpers

/** Check existence of the [prefixWithKeyRange] on [iterator] by checking existence of creation time */
internal fun checkExistence(
    iterator: FDBIterator,
    prefixWithKeyRange: ByteArray
) {
    if (!iterator.hasNext() || !iterator.next().key.contentEquals(prefixWithKeyRange)) {
        throw Exception("Key does not exist while it should have existed")
    }
}
