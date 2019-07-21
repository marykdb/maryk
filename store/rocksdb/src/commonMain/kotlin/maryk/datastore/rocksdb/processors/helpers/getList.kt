package maryk.datastore.rocksdb.processors.helpers

import maryk.core.extensions.bytes.initIntByVar
import maryk.core.properties.references.ListReference
import maryk.core.properties.types.Key
import maryk.datastore.rocksdb.TableColumnFamilies
import maryk.lib.extensions.compare.matchPart
import maryk.rocksdb.ReadOptions
import maryk.rocksdb.Transaction
import maryk.rocksdb.use

/**
 * Get list from [transaction] at [reference] by reading and collecting all values from DataRecord
 */
internal fun <T : Any> getList(
    transaction: Transaction,
    columnFamilies: TableColumnFamilies,
    readOptions: ReadOptions,
    key: Key<*>,
    reference: ListReference<T, *>
): MutableList<T>? {
    val keyAndReference = reference.toStorageByteArray(key.bytes)

    transaction.getIterator(readOptions, columnFamilies.table).use { iterator ->
        iterator.seek(keyAndReference)

        // First handle the count
        var ref = iterator.key()
        val count = if (!iterator.isValid() || !ref.matchPart(0, keyAndReference)) {
            return mutableListOf()
        } else {
            var readIndex = ULong.SIZE_BYTES
            val countBytes = iterator.value()
            initIntByVar { countBytes[readIndex++] }
        }

        val list = ArrayList<T>(count)

        iterator.next() // skip the count. It was read above
        while (iterator.isValid()) {
            ref = iterator.key()

            if (ref.matchPart(0, keyAndReference)) {
                val valueAsBytes = iterator.value()
                var readIndex = ULong.SIZE_BYTES // Skip version because reading from main table
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

        iterator.close()
        return list
    }
}
