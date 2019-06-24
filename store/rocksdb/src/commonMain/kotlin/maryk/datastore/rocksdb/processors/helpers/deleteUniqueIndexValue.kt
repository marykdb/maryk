package maryk.datastore.rocksdb.processors.helpers

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
    version: ByteArray
) {
    transaction.delete(columnFamilies.unique, byteArrayOf(*indexReference, *value))
    if (columnFamilies is HistoricTableColumnFamilies) {
        transaction.put(columnFamilies.unique, byteArrayOf(*indexReference, *value, *version), FALSE_ARRAY)
    }
}
