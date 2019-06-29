package maryk.datastore.rocksdb.processors.helpers

import maryk.core.extensions.bytes.invert
import maryk.datastore.rocksdb.HistoricTableColumnFamilies
import maryk.datastore.rocksdb.TableColumnFamilies
import maryk.datastore.rocksdb.processors.FALSE_ARRAY
import maryk.rocksdb.Transaction

/** Delete a [value] from the unique index at [indexReference] and stores it as [version] */
internal fun deleteUniqueIndexValue(
    transaction: Transaction,
    columnFamilies: TableColumnFamilies,
    indexReference: ByteArray,
    value: ByteArray,
    valueOffset: Int,
    valueLength: Int,
    version: ByteArray
) {
    val reference = ByteArray(indexReference.size + valueLength)
    indexReference.copyInto(reference)
    value.copyInto(reference, indexReference.size, valueOffset, valueLength + valueOffset)
    transaction.delete(columnFamilies.unique, reference)

    if (columnFamilies is HistoricTableColumnFamilies) {
        val historicReference = byteArrayOf(*reference, *version)
        // Invert so the time is sorted in reverse order with newest on top
        historicReference.invert(reference.size)

        transaction.put(columnFamilies.unique, historicReference, FALSE_ARRAY)
    }
}
