package maryk.datastore.rocksdb

import maryk.rocksdb.ColumnFamilyHandle
import maryk.rocksdb.ReadOptions
import maryk.rocksdb.Transaction as RocksTransaction
import maryk.rocksdb.WriteOptions
import maryk.rocksdb.rocksDBNotFound
import kotlin.math.min

/**
 * Wrapper around RocksDB [RocksTransaction] so the store can use the
 * transaction implementation provided by RocksDB instead of the previous
 * custom version.
 */
class Transaction(val rocksDBDataStore: RocksDBDataStore) : DBAccessor(rocksDBDataStore), AutoCloseable {
    private val writeOptions = WriteOptions()
    private val transaction: RocksTransaction = rocksDBDataStore.db.beginTransaction(writeOptions)

    fun put(columnFamilyHandle: ColumnFamilyHandle, key: ByteArray, value: ByteArray) {
        transaction.put(columnFamilyHandle, key, value)
    }

    fun delete(columnFamilyHandle: ColumnFamilyHandle, key: ByteArray) {
        transaction.delete(columnFamilyHandle, key)
    }

    override fun get(
        columnFamilyHandle: ColumnFamilyHandle,
        readOptions: ReadOptions,
        key: ByteArray
    ): ByteArray? = transaction.get(readOptions, columnFamilyHandle, key)

    override fun get(
        columnFamilyHandle: ColumnFamilyHandle,
        readOptions: ReadOptions,
        key: ByteArray,
        offset: Int,
        len: Int,
        value: ByteArray,
        vOffset: Int,
        vLen: Int
    ): Int {
        val result = transaction.get(readOptions, columnFamilyHandle, key.copyOfRange(offset, offset + len))
        return if (result != null) {
            val size = result.size
            val copyLength = min(size, vLen)
            result.copyInto(value, vOffset, 0, copyLength)
            size
        } else {
            rocksDBNotFound
        }
    }

    override fun getIterator(readOptions: ReadOptions, columnFamilyHandle: ColumnFamilyHandle) =
        DBIterator(transaction.getIterator(readOptions, columnFamilyHandle))

    fun getForUpdate(
        readOptions: ReadOptions,
        columnFamilyHandle: ColumnFamilyHandle,
        key: ByteArray
    ): ByteArray? = transaction.getForUpdate(readOptions, columnFamilyHandle, key, true)

    fun setSavePoint() {
        transaction.setSavePoint()
    }

    fun rollbackToSavePoint() {
        transaction.rollbackToSavePoint()
    }

    fun commit() {
        transaction.commit()
    }

    override fun close() {
        transaction.close()
        writeOptions.close()
    }
}

