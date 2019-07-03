package maryk.datastore.rocksdb.processors.helpers

import maryk.core.extensions.bytes.invert
import maryk.datastore.rocksdb.HistoricTableColumnFamilies
import maryk.datastore.rocksdb.TableColumnFamilies
import maryk.datastore.rocksdb.processors.FALSE_ARRAY
import maryk.rocksdb.Transaction

/** Delete the [indexReference] and [valueAndKey] for [version] */
internal fun deleteIndexValue(
    transaction: Transaction,
    columnFamilies: TableColumnFamilies,
    indexReference: ByteArray,
    valueAndKey: ByteArray,
    version: ByteArray,
    hardDelete: Boolean = false
) {
    transaction.delete(columnFamilies.index, byteArrayOf(*indexReference, *valueAndKey))

    // Only delete with non hard deletes since with hard deletes all values are deleted
    if (!hardDelete && columnFamilies is HistoricTableColumnFamilies) {
        val historicReference = byteArrayOf(*indexReference, *version)
        // Invert so the time is sorted in reverse order with newest on top
        historicReference.invert(historicReference.size - version.size)

        transaction.put(
            columnFamilies.historic.index,
            historicReference,
            FALSE_ARRAY
        )
    }
}
