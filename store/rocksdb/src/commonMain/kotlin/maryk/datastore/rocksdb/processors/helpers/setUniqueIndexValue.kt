package maryk.datastore.rocksdb.processors.helpers

import maryk.core.properties.types.Key
import maryk.datastore.rocksdb.HistoricTableColumnFamilies
import maryk.datastore.rocksdb.TableColumnFamilies
import maryk.rocksdb.Transaction

/** Set the [uniqueReferenceWithValue] to [key] for [version] */
internal fun setUniqueIndexValue(
    columnFamilies: TableColumnFamilies,
    transaction: Transaction,
    uniqueReferenceWithValue: ByteArray,
    version: ByteArray,
    key: Key<*>
) {
    transaction.put(columnFamilies.unique, uniqueReferenceWithValue, key.bytes)
    if (columnFamilies is HistoricTableColumnFamilies) {
        transaction.put(
            columnFamilies.historic.unique,
            byteArrayOf(*uniqueReferenceWithValue, *version),
            key.bytes
        )
    }
}
