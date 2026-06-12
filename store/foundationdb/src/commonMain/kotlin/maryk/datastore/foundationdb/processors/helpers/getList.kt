package maryk.datastore.foundationdb.processors.helpers

import maryk.core.exceptions.StorageException
import maryk.core.extensions.bytes.initIntByVar
import maryk.core.properties.references.ListReference
import maryk.core.properties.types.Key
import maryk.datastore.foundationdb.IsTableDirectories
import maryk.datastore.shared.readValue
import maryk.foundationdb.Range
import maryk.foundationdb.Transaction

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
    val iterator = this.getRange(Range.startsWith(prefix)).iterator()
    if (!iterator.hasNext()) return mutableListOf()

    // First entry holds the count (version || varint count)
    val count = readStoredListCount(iterator.nextBlocking().value)

    val list = ArrayList<T>(count)
    // Process item values
    while (iterator.hasNext()) {
        val valueBytes = iterator.nextBlocking().value
        requireVersionedValue(valueBytes)
        var idx = VERSION_BYTE_SIZE
        val reader = { valueBytes[idx++] }
        @Suppress("UNCHECKED_CAST")
        readValue(reference.comparablePropertyDefinition.valueDefinition, reader) {
            valueBytes.size - idx
        }?.let { list.add(it as T) }
    }
    return list
}

internal fun readStoredListCount(countValue: ByteArray): Int {
    requireVersionedValue(countValue)
    var readIndex = VERSION_BYTE_SIZE
    return try {
        initIntByVar { countValue[readIndex++] }
    } catch (cause: IndexOutOfBoundsException) {
        throw StorageException("Invalid stored list count: ${cause.message}")
    }
}

internal fun requireVersionedValue(valueBytes: ByteArray) = requireVersionedValueSize(valueBytes.size)

internal fun requireVersionedValueSize(valueSize: Int) {
    if (valueSize < VERSION_BYTE_SIZE) {
        throw StorageException("Stored value is missing version prefix: $valueSize bytes")
    }
}
