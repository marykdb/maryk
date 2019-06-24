package maryk.datastore.rocksdb.processors.helpers

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
        transaction.put(
            columnFamilies.historic.index,
            byteArrayOf(*indexReference, *valueAndKey, *version),
            TRUE_ARRAY
        )
    }
}
