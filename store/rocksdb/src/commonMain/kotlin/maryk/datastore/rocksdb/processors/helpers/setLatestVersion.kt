package maryk.datastore.rocksdb.processors.helpers

import maryk.core.properties.types.Key
import maryk.datastore.rocksdb.TableColumnFamilies
import maryk.datastore.rocksdb.processors.LAST_VERSION_INDICATOR
import maryk.datastore.rocksdb.Transaction

/** Set the latest [version] for [key] */
internal fun setLatestVersion(
    transaction: Transaction,
    columnFamilies: TableColumnFamilies,
    key: Key<*>,
    version: ByteArray
) {
    val lastVersionRef = byteArrayOf(*key.bytes, LAST_VERSION_INDICATOR)
    transaction.put(columnFamilies.table, lastVersionRef, version)
}
