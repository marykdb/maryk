package maryk.datastore.rocksdb.processors.helpers

import maryk.datastore.rocksdb.DBAccessor
import maryk.datastore.rocksdb.TableColumnFamilies
import maryk.lib.recyclableByteArray
import maryk.rocksdb.ReadOptions
import maryk.rocksdb.rocksDBNotFound

internal fun readCreationVersion(
    dbAccessor: DBAccessor,
    columnFamilies: TableColumnFamilies,
    readOptions: ReadOptions,
    key: ByteArray
): ULong? {
    return when (dbAccessor.get(columnFamilies.table, readOptions, key, recyclableByteArray)) {
        rocksDBNotFound -> null
        else -> recyclableByteArray.readVersionBytes()
    }
}
