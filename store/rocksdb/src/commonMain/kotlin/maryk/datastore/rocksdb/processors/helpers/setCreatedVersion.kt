package maryk.datastore.rocksdb.processors.helpers

import maryk.core.properties.types.Key
import maryk.datastore.rocksdb.HistoricTableColumnFamilies
import maryk.datastore.rocksdb.TableColumnFamilies
import maryk.rocksdb.Transaction

/** Set the created [version] for [key] */
internal fun setCreatedVersion(
    transaction: Transaction,
    columnFamilies: TableColumnFamilies,
    key: Key<*>,
    version: ByteArray
) {
    transaction.put(columnFamilies.keys, key.bytes, version)
    transaction.put(columnFamilies.table, key.bytes, version)
    if (columnFamilies is HistoricTableColumnFamilies) {
        transaction.put(columnFamilies.historic.table, key.bytes, version)
    }
}
