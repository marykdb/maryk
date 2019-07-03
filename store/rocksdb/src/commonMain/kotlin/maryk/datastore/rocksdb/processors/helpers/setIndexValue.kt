package maryk.datastore.rocksdb.processors.helpers

import maryk.core.extensions.bytes.invert
import maryk.datastore.rocksdb.HistoricTableColumnFamilies
import maryk.datastore.rocksdb.TableColumnFamilies
import maryk.datastore.rocksdb.processors.TRUE_ARRAY
import maryk.rocksdb.Transaction

/** Set the [indexReference] and [valueAndKey] for [version] */
internal fun setIndexValue(
    transaction: Transaction,
    columnFamilies: TableColumnFamilies,
    indexReference: ByteArray,
    valueAndKey: ByteArray,
    version: ByteArray
) {
    transaction.put(columnFamilies.index, byteArrayOf(*indexReference, *valueAndKey), TRUE_ARRAY)
    if (columnFamilies is HistoricTableColumnFamilies) {
        val historicReference = byteArrayOf(*indexReference, *valueAndKey, *version)
        // Invert so the time is sorted in reverse order with newest on top
        historicReference.invert(historicReference.size - version.size)

        transaction.put(
            columnFamilies.historic.index,
            historicReference,
            TRUE_ARRAY
        )
    }
}
