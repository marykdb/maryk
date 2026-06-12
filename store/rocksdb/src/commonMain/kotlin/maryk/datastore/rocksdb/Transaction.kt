package maryk.datastore.rocksdb

import maryk.lib.extensions.compare.compareTo
import maryk.rocksdb.ColumnFamilyHandle
import maryk.rocksdb.GetStatus
import maryk.rocksdb.ReadOptions
import maryk.rocksdb.RocksDBException
import maryk.rocksdb.StatusCode
import maryk.rocksdb.Transaction as RocksDBTransaction
import maryk.rocksdb.getRequiredSize
import maryk.rocksdb.getStatus
import maryk.rocksdb.rocksDBNotFound

private const val DELETE_RANGE_BATCH_SIZE = 1024

class Transaction(val rocksDBDataStore: RocksDBDataStore): DBAccessor(rocksDBDataStore) {
    private var nativeTransaction: RocksDBTransaction? = null

    fun put(columnFamilyHandle: ColumnFamilyHandle, key: ByteArray, value: ByteArray) {
        currentTransaction().put(columnFamilyHandle, key, value)
    }

    fun delete(columnFamilyHandle: ColumnFamilyHandle, key: ByteArray) {
        currentTransaction().delete(columnFamilyHandle, key)
    }

    fun deleteRange(columnFamilyHandle: ColumnFamilyHandle, start: ByteArray, end: ByteArray) {
        val transaction = currentTransaction()
        do {
            val keysToDelete = ArrayList<ByteArray>(DELETE_RANGE_BATCH_SIZE)
            transaction.getIterator(rocksDBDataStore.defaultReadOptions, columnFamilyHandle).use { iterator ->
                iterator.seek(start)
                while (
                    iterator.isValid() &&
                    iterator.key() < end &&
                    keysToDelete.size < DELETE_RANGE_BATCH_SIZE
                ) {
                    keysToDelete.add(iterator.key())
                    iterator.next()
                }
            }

            for (key in keysToDelete) {
                transaction.delete(columnFamilyHandle, key)
            }
        } while (keysToDelete.size == DELETE_RANGE_BATCH_SIZE)
    }

    override fun get(columnFamilyHandle: ColumnFamilyHandle, readOptions: ReadOptions, key: ByteArray): ByteArray? =
        currentTransaction().get(readOptions, columnFamilyHandle, key)

    override fun get(columnFamilyHandle: ColumnFamilyHandle, readOptions: ReadOptions, key: ByteArray, offset: Int, len: Int, value: ByteArray, vOffset: Int, vLen: Int): Int {
        val readKey = if (offset == 0 && len == key.size) key else key.copyOfRange(offset, offset + len)
        val targetSize = minOf(vLen, value.size - vOffset)
        val status = if (vOffset == 0 && targetSize == value.size) {
            currentTransaction().get(readOptions, columnFamilyHandle, readKey, value)
        } else {
            val buffer = ByteArray(targetSize)
            currentTransaction().get(readOptions, columnFamilyHandle, readKey, buffer).also {
                if (it.getStatus().getCode() != StatusCode.Ok) return it.requiredSizeOrNotFound()
                buffer.copyInto(value, vOffset, 0, minOf(buffer.size, value.size - vOffset))
            }
        }
        return status.requiredSizeOrNotFound()
    }

    override fun getIterator(readOptions: ReadOptions, columnFamilyHandle: ColumnFamilyHandle) =
        DBIterator(currentTransaction().getIterator(readOptions, columnFamilyHandle))

    fun getForUpdate(
        readOptions: ReadOptions,
        columnFamilyHandle: ColumnFamilyHandle,
        key: ByteArray
    ) = currentTransaction().getForUpdate(readOptions, columnFamilyHandle, key, true)

    fun commit() {
        val transaction = nativeTransaction ?: return
        nativeTransaction = null
        try {
            transaction.commit()
        } finally {
            transaction.close()
        }
    }

    fun setSavePoint() {
        currentTransaction().setSavePoint()
    }

    fun rollbackToSavePoint() {
        currentTransaction().rollbackToSavePoint()
    }

    override fun close() {
        nativeTransaction?.close()
        nativeTransaction = null
    }

    private fun currentTransaction() =
        nativeTransaction ?: rocksDBDataStore.db.beginTransaction(rocksDBDataStore.defaultWriteOptions).also {
            nativeTransaction = it
        }
}

private fun GetStatus.requiredSizeOrNotFound() = when (getStatus().getCode()) {
    StatusCode.Ok -> getRequiredSize()
    StatusCode.NotFound -> rocksDBNotFound
    else -> throw RocksDBException(getStatus())
}

internal suspend fun <T> RocksDBDataStore.withTransaction(
    block: suspend (Transaction) -> T
): T {
    val transaction = Transaction(this)
    try {
        return block(transaction)
    } finally {
        transaction.close()
    }
}
