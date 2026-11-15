package maryk.datastore.rocksdb.processors.helpers

import maryk.core.properties.types.Key
import maryk.datastore.rocksdb.DBIterator
import maryk.lib.extensions.compare.matchesRangePart

/** Find non historic qualifiers on [iterator] for [key] */
internal fun DBIterator.nonHistoricQualifierRetriever(
    key: Key<*>
): (((Int) -> Byte, Int) -> Unit) -> Boolean {
    val keyBytes = key.bytes
    return { resultHandler ->
        next()
        if (!isValid()) {
            false
        } else {
            val qualifier: ByteArray = key()
            if (!qualifier.matchesRangePart(0, keyBytes)) {
                false
            } else {
                resultHandler({ qualifier[keyBytes.size + it] }, qualifier.size - keyBytes.size)
                true
            }
        }
    }
}
