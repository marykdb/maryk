package maryk.datastore.rocksdb.processors.helpers

import maryk.core.extensions.bytes.invert
import maryk.datastore.rocksdb.HistoricTableColumnFamilies
import maryk.datastore.rocksdb.TableColumnFamilies
import maryk.datastore.rocksdb.Transaction
import maryk.datastore.rocksdb.processors.EMPTY_ARRAY

/** Delete a [value] from the unique index at [indexReference] and stores it as [version] */
internal fun deleteUniqueIndexValue(
    transaction: Transaction,
    columnFamilies: TableColumnFamilies,
    indexReference: ByteArray,
    value: ByteArray,
    valueOffset: Int,
    valueLength: Int,
    version: ByteArray,
    hardDelete: Boolean = false
) {
    val reference = ByteArray(indexReference.size + valueLength)
    indexReference.copyInto(reference)
    value.copyInto(reference, indexReference.size, valueOffset, valueLength + valueOffset)
    transaction.delete(columnFamilies.unique, reference)

    // Only add a delete marker when not a hard delete. With hard delete all historic values are deleted
    if (!hardDelete && columnFamilies is HistoricTableColumnFamilies) {
        val historicReference = byteArrayOf(*reference, *version)
        // Invert so the time is sorted in reverse order with newest on top
        historicReference.invert(reference.size)

        transaction.put(columnFamilies.unique, historicReference, EMPTY_ARRAY)
    }
}
