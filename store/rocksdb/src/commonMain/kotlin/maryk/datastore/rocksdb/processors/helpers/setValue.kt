package maryk.datastore.rocksdb.processors.helpers

import maryk.core.properties.types.Key
import maryk.datastore.rocksdb.HistoricTableColumnFamilies
import maryk.datastore.rocksdb.TableColumnFamilies
import maryk.rocksdb.Transaction

/** Set [reference] = [value] (ByteArray) at [version] for object at [key] */
internal fun setValue(
    transaction: Transaction,
    columnFamilies: TableColumnFamilies,
    key: Key<*>,
    reference: ByteArray,
    version: ByteArray,
    value: ByteArray
) {
    transaction.put(
        columnFamilies.table,
        byteArrayOf(*key.bytes, *reference),
        byteArrayOf(*version, *value)
    )

    if (columnFamilies is HistoricTableColumnFamilies) {
        transaction.put(
            columnFamilies.historic.table,
            byteArrayOf(*key.bytes, *reference, *version),
            value
        )
    }
}
