package maryk.datastore.foundationdb.processors.helpers

import com.apple.foundationdb.Range
import com.apple.foundationdb.Transaction
import maryk.core.extensions.bytes.initIntByVar
import maryk.core.properties.references.ListReference
import maryk.core.properties.types.Key
import maryk.datastore.foundationdb.IsTableDirectories
import maryk.datastore.shared.readValue

/**
 * Read a list for [reference] from FoundationDB by scanning the qualifiers under the reference prefix.
 */
internal fun <T : Any> Transaction.getList(
    tableDirs: IsTableDirectories,
    key: Key<*>,
    reference: ListReference<T, *>
): MutableList<T> {
    val keyBytes = key.bytes
    val referenceBytes = reference.toStorageByteArray()

    val prefix = packKey(tableDirs.tablePrefix, keyBytes, referenceBytes)
    FDBIterator(this.getRange(Range.startsWith(prefix)).iterator()).use { iterator ->
        if (!iterator.hasNext()) return mutableListOf()

        // First entry holds the count (version || varint count)
        val countValue = iterator.next().value
        var readIndex = VERSION_BYTE_SIZE
        val count = initIntByVar { countValue[readIndex++] }

        val list = ArrayList<T>(count)
        // Process item values
        while (iterator.hasNext()) {
            val valueBytes = iterator.next().value
            var idx = VERSION_BYTE_SIZE
            val reader = { valueBytes[idx++] }
            @Suppress("UNCHECKED_CAST")
            readValue(reference.comparablePropertyDefinition.valueDefinition, reader) {
                valueBytes.size - idx
            }?.let { list.add(it as T) }
        }
        return list
    }
}
