package maryk.datastore.rocksdb

import maryk.rocksdb.AutoCloseable
import maryk.rocksdb.ColumnFamilyHandle
import maryk.rocksdb.ReadOptions
import maryk.rocksdb.RocksDB

open class DBAccessor(
    internal val rocksDB: RocksDB
): AutoCloseable {
    open fun get(columnFamilyHandle: ColumnFamilyHandle, readOptions: ReadOptions, key: ByteArray) =
        rocksDB.get(columnFamilyHandle, readOptions, key)

    open fun getIterator(readOptions: ReadOptions, columnFamilyHandle: ColumnFamilyHandle) =
        DBIterator(
            rocksDB.newIterator(columnFamilyHandle, readOptions)
        )

    override fun close() {
        // Not needed for the moment
    }
}
