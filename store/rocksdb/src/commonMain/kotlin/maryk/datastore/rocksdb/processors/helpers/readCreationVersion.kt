package maryk.datastore.rocksdb.processors.helpers

import maryk.datastore.rocksdb.DBAccessor
import maryk.datastore.rocksdb.TableColumnFamilies
import maryk.lib.recyclableByteArray
import org.rocksdb.ReadOptions
import org.rocksdb.RocksDB

internal fun readCreationVersion(
    dbAccessor: DBAccessor,
    columnFamilies: TableColumnFamilies,
    readOptions: ReadOptions,
    key: ByteArray
): ULong? {
    return when (dbAccessor.get(columnFamilies.table, readOptions, key, recyclableByteArray)) {
        RocksDB.NOT_FOUND -> null
        else -> recyclableByteArray.readVersionBytes()
    }
}
