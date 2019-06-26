package maryk.datastore.rocksdb.processors.helpers

import maryk.core.properties.types.Key
import maryk.lib.extensions.compare.matchPart
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

        if (qualifier != null && qualifier.matchPart(0, key.bytes)) {
            qualifier.copyOfRange(key.bytes.size, qualifier.size)
        } else null
    }
}
