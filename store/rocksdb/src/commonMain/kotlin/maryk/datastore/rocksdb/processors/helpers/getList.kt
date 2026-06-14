package maryk.datastore.rocksdb.processors.helpers

import maryk.core.exceptions.StorageException
import maryk.core.extensions.bytes.initIntByVar
import maryk.core.properties.references.ListReference
import maryk.core.properties.types.Key
import maryk.datastore.rocksdb.DBAccessor
import maryk.datastore.rocksdb.TableColumnFamilies
import maryk.datastore.shared.readValue
import maryk.lib.exceptions.ParseException
import maryk.lib.extensions.compare.matchesRangePart
import maryk.rocksdb.ReadOptions

/**
 * Get list from [dbAccessor] at [reference] by reading and collecting all values from DataRecord
 */
internal fun <T : Any> getList(
    dbAccessor: DBAccessor,
    columnFamilies: TableColumnFamilies,
    readOptions: ReadOptions,
    key: Key<*>,
    reference: ListReference<T, *>
): MutableList<T> {
    val keyAndReference = reference.toStorageByteArray(key.bytes)

    dbAccessor.getIterator(readOptions, columnFamilies.table).use { iterator ->
        iterator.seek(keyAndReference)

        // First handle the count
        var ref = iterator.key()
        val count = if (!iterator.isValid() || !ref.matchesRangePart(0, keyAndReference)) {
            return mutableListOf()
        } else {
            readStoredListCount(iterator.value())
        }

        val list = ArrayList<T>(count)

        iterator.next() // skip the count. It was read above
        while (iterator.isValid()) {
            ref = iterator.key()

            if (ref.matchesRangePart(0, keyAndReference)) {
                val valueAsBytes = iterator.value()
                requireVersionedValue(valueAsBytes)
                var readIndex = VERSION_BYTE_SIZE // Skip version because reading from main table
                val reader = { valueAsBytes[readIndex++] }
                readValue(reference.comparablePropertyDefinition.valueDefinition, reader) {
                    valueAsBytes.size - readIndex
                }.also {
                    @Suppress("UNCHECKED_CAST")
                    list.add(it as T)
                }
            } else {
                break
            }
            iterator.next()
        }

        return list
    }
}

internal fun readStoredListCount(countBytes: ByteArray): Int {
    requireVersionedValue(countBytes)
    var readIndex = VERSION_BYTE_SIZE
    return try {
        initIntByVar { countBytes[readIndex++] }
    } catch (cause: ParseException) {
        throw StorageException("Invalid stored list count: ${cause.message}")
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
