package maryk.datastore.rocksdb

import maryk.rocksdb.AutoCloseable
import maryk.rocksdb.ColumnFamilyHandle
import maryk.rocksdb.ReadOptions
import maryk.rocksdb.RocksDB

open class DBAccessor(
    internal val rocksDB: RocksDB
): AutoCloseable {
    fun get(columnFamilyHandle: ColumnFamilyHandle, readOptions: ReadOptions, key: ByteArray, value: ByteArray) =
        get(columnFamilyHandle, readOptions, key, 0, key.size, value, 0, value.size)

    open fun get(columnFamilyHandle: ColumnFamilyHandle, readOptions: ReadOptions, key: ByteArray, offset: Int, len: Int, value: ByteArray, vOffset: Int = 0, vLen: Int = value.size) =
        rocksDB.get(columnFamilyHandle, readOptions, key, offset, len, value, vOffset, vLen)

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
