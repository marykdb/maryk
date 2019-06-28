package maryk.datastore.rocksdb.processors.helpers

import maryk.core.properties.types.Key
import maryk.rocksdb.RocksIterator

/** Find non historic qualifiers on [iterator] for [key] */
fun RocksIterator.nonHistoricQualifierRetriever(
    key: Key<*>
): () -> ByteArray? = {
    next()
    if (!isValid()) {
        null
    } else {
        val qualifier: ByteArray? = key()

        // key range check is ensured with setPrefixSameAsStart
        qualifier?.copyOfRange(key.bytes.size, qualifier.size)
    }
}
