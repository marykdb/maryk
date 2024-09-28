package maryk.datastore.rocksdb.processors.helpers

import maryk.core.extensions.bytes.invert
import maryk.datastore.rocksdb.HistoricTableColumnFamilies
import maryk.datastore.rocksdb.TableColumnFamilies
import maryk.datastore.rocksdb.Transaction
import maryk.datastore.rocksdb.processors.EMPTY_ARRAY
import maryk.lib.bytes.combineToByteArray

/** Set the [indexReference] and [valueAndKey] for [version] */
internal fun setIndexValue(
    transaction: Transaction,
    columnFamilies: TableColumnFamilies,
    indexReference: ByteArray,
    valueAndKey: ByteArray,
    version: ByteArray
) {
    transaction.put(columnFamilies.index, indexReference + valueAndKey, version)
    if (columnFamilies is HistoricTableColumnFamilies) {
        val historicReference = combineToByteArray(indexReference, valueAndKey, version)
        // Invert so the time is sorted in reverse order with newest on top
        historicReference.invert(historicReference.size - version.size)

        transaction.put(
            columnFamilies.historic.index,
            historicReference,
            EMPTY_ARRAY
        )
    }
}
