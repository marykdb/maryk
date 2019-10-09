package maryk.datastore.rocksdb.processors.helpers

import maryk.core.properties.types.Key
import maryk.datastore.rocksdb.DBIterator

/** Find non historic qualifiers on [iterator] for [key] */
internal fun DBIterator.nonHistoricQualifierRetriever(
    key: Key<*>
): (((Int) -> Byte, Int) -> Unit) -> Boolean = { resultHandler ->
    next()
    if (!isValid()) {
        false
    } else {
        val qualifier: ByteArray = key()
        resultHandler({ qualifier[key.bytes.size + it] }, qualifier.size - key.bytes.size)
        true
    }
}
