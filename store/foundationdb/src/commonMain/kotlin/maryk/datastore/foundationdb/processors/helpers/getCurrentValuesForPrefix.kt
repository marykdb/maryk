package maryk.datastore.foundationdb.processors.helpers

import com.apple.foundationdb.Range
import com.apple.foundationdb.Transaction
import maryk.core.properties.types.Key
import maryk.datastore.foundationdb.IsTableDirectories

/**
 * Retrieve all current values for a given reference prefix under a specific key.
 * Returns a list of qualifier/value pairs where the qualifier is relative to the table+key prefix.
 */
internal fun getCurrentValuesForPrefix(
    tr: Transaction,
    tableDirs: IsTableDirectories,
    key: Key<*>,
    referencePrefix: ByteArray,
): List<Pair<ByteArray, ByteArray>> {
    val prefix = packKey(tableDirs.tablePrefix, key.bytes, referencePrefix)
    val list = mutableListOf<Pair<ByteArray, ByteArray>>()
    FDBIterator(tr.getRange(Range.startsWith(prefix)).iterator()).use { iterator ->
        while (iterator.hasNext()) {
            val kv = iterator.next()
            // Extract qualifier after (tablePrefix + key)
            val qualifier = kv.key.copyOfRange(prefix.size - referencePrefix.size, kv.key.size)
            list += qualifier to kv.value
        }
    }
    return list
}
