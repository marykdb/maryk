package maryk.datastore.rocksdb.processors.helpers

import maryk.core.properties.types.Key
import maryk.datastore.rocksdb.TableColumnFamilies
import maryk.datastore.rocksdb.Transaction
import maryk.lib.extensions.compare.compareDefinedRange

internal fun getCurrentValues(
    transaction: Transaction,
    columnFamilies: TableColumnFamilies,
    key: Key<*>,
    referenceAsBytes: ByteArray
): List<Pair<ByteArray, ByteArray>> {
    val prefix = key.bytes + referenceAsBytes
    val currentValues = mutableListOf<Pair<ByteArray, ByteArray>>()

    val iterator = transaction.getIterator(transaction.rocksDBDataStore.defaultReadOptions, columnFamilies.table)
    iterator.seek(prefix)

    while (iterator.isValid() && prefix.compareDefinedRange(iterator.key()) == 0) {
        currentValues.add(iterator.key() to iterator.value())
        iterator.next()
    }

    return currentValues
}
