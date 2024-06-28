package maryk.datastore.rocksdb

import org.rocksdb.ColumnFamilyHandle
import org.rocksdb.ReadOptions

open class DBAccessor(
    internal val dataStore: RocksDBDataStore
): AutoCloseable {
    fun get(columnFamilyHandle: ColumnFamilyHandle, readOptions: ReadOptions, key: ByteArray, value: ByteArray) =
        get(columnFamilyHandle, readOptions, key, 0, key.size, value, 0, value.size)

    open fun get(columnFamilyHandle: ColumnFamilyHandle, readOptions: ReadOptions, key: ByteArray, offset: Int, len: Int, value: ByteArray, vOffset: Int = 0, vLen: Int = value.size) =
        dataStore.db.get(columnFamilyHandle, readOptions, key, offset, len, value, vOffset, vLen)

    open fun get(columnFamilyHandle: ColumnFamilyHandle, readOptions: ReadOptions, key: ByteArray): ByteArray? =
        dataStore.db.get(columnFamilyHandle, readOptions, key)

    open fun getIterator(readOptions: ReadOptions, columnFamilyHandle: ColumnFamilyHandle) =
        DBIterator(
            dataStore.db.newIterator(columnFamilyHandle, readOptions)
        )

    override fun close() {
        // Not needed for the moment
    }
}
