package maryk.datastore.rocksdb.processors.helpers

import maryk.core.extensions.bytes.toULong
import maryk.datastore.rocksdb.TableColumnFamilies
import maryk.datastore.rocksdb.Transaction
import maryk.lib.recyclableByteArray
import maryk.rocksdb.ReadOptions
import maryk.rocksdb.rocksDBNotFound

internal fun readCreationVersion(
    transaction: Transaction,
    columnFamilies: TableColumnFamilies,
    readOptions: ReadOptions,
    key: ByteArray
): ULong? {
    return when (transaction.get(columnFamilies.table, readOptions, key, recyclableByteArray)) {
        rocksDBNotFound -> null
        else -> recyclableByteArray.toULong()
    }
}
