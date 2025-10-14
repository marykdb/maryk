package maryk.datastore.rocksdb

import maryk.datastore.rocksdb.ChangeAction.Delete
import maryk.datastore.rocksdb.ChangeAction.Put
import maryk.lib.extensions.compare.compareTo
import maryk.lib.extensions.compare.compareToWithOffsetLength
import maryk.lib.extensions.toHex
import maryk.rocksdb.ColumnFamilyHandle
import maryk.rocksdb.ReadOptions
import maryk.rocksdb.RocksDBException
import maryk.rocksdb.rocksDBNotFound

internal sealed class ChangeAction(
    val columnFamilyHandle: ColumnFamilyHandle,
    val key: ByteArray
) {
    class Put(columnFamilyHandle: ColumnFamilyHandle, key: ByteArray, val value: ByteArray) : ChangeAction(columnFamilyHandle, key)
    class Delete(columnFamilyHandle: ColumnFamilyHandle, key: ByteArray) : ChangeAction(columnFamilyHandle, key)
}

private data class CheckBeforeCommit(
    val columnFamilyHandle: ColumnFamilyHandle,
    val key: ByteArray,
    val value: ByteArray?
)

class Transaction(val rocksDBDataStore: RocksDBDataStore): DBAccessor(rocksDBDataStore) {
    private var savedChanges: Map<Int, List<ChangeAction>>? = null
    // Changes sorted on the column family ID
    internal val changes = mutableMapOf<Int, MutableList<ChangeAction>>()
    private val checksBeforeCommit = mutableListOf<CheckBeforeCommit>()

    fun put(columnFamilyHandle: ColumnFamilyHandle, key: ByteArray, value: ByteArray) {
        setChange(Put(columnFamilyHandle, key, value))
    }

    fun delete(columnFamilyHandle: ColumnFamilyHandle, key: ByteArray) {
        setChange(Delete(columnFamilyHandle, key))
    }

    override fun get(columnFamilyHandle: ColumnFamilyHandle, readOptions: ReadOptions, key: ByteArray): ByteArray? {
        val columnChange = getChangeOrNull(columnFamilyHandle, key)

        return when (val change = columnChange) {
            null -> dataStore.db.get(columnFamilyHandle, readOptions, key)
            is Put -> change.value
            is Delete -> null
        }
    }

    override fun get(columnFamilyHandle: ColumnFamilyHandle, readOptions: ReadOptions, key: ByteArray, offset: Int, len: Int, value: ByteArray, vOffset: Int, vLen: Int): Int {
        val change = getChangeOrNull(columnFamilyHandle, key, offset, len)
        return when (change) {
            is Put -> {
                change.value.copyInto(value, vOffset)
                change.value.size
            }
            is Delete -> rocksDBNotFound
            else -> super.get(columnFamilyHandle, readOptions, key, offset, len, value, vOffset, vLen)
        }
    }

    override fun getIterator(readOptions: ReadOptions, columnFamilyHandle: ColumnFamilyHandle) =
        TransactionIterator(
            this,
            columnFamilyHandle,
            dataStore.db.newIterator(columnFamilyHandle, readOptions)
        )

    fun getForUpdate(
        readOptions: ReadOptions,
        columnFamilyHandle: ColumnFamilyHandle,
        key: ByteArray
    ) = this.get(columnFamilyHandle, readOptions, key).also { value ->
        setCheckBeforeCommit(columnFamilyHandle, key, value)
    }

    fun commit() {
        checksBeforeCommit.forEach { check ->
            val current = dataStore.db.get(check.columnFamilyHandle, check.key)

            if (check.value == null) {
                if (current != null) {
                    throw RocksDBException("Merge error, key ${check.key.toHex()} changed before commit")
                }
            } else if (current == null || check.value compareTo current != 0) {
                throw RocksDBException("Merge error, key ${check.key.toHex()} changed before commit")
            }
        }

        changes.values.flatten().forEach { change ->
            when (change) {
                is Put -> dataStore.db.put(change.columnFamilyHandle, change.key, change.value)
                is Delete -> dataStore.db.delete(change.columnFamilyHandle, change.key)
            }
        }
        changes.clear()
        savedChanges = null
        checksBeforeCommit.clear()
    }

    fun setSavePoint() {
        savedChanges = changes.mapValues { it.value.toList() }
    }

    fun rollbackToSavePoint() {
        changes.clear()
        savedChanges?.let { saved ->
            changes.putAll(saved.mapValues { it.value.toMutableList() })
        }
    }

    private fun setChange(action: ChangeAction) {
        val columnChanges = changes.getOrPut(action.columnFamilyHandle.getID()) { mutableListOf() }
        val index = columnChanges.binarySearch { it.key compareTo action.key }
        if (index >= 0) {
            columnChanges[index] = action
        } else {
            columnChanges.add(index * -1 - 1, action)
        }
    }

    private fun setCheckBeforeCommit(columnFamilyHandle: ColumnFamilyHandle, key: ByteArray, value: ByteArray?) {
        val check = CheckBeforeCommit(columnFamilyHandle, key, value)
        val index = checksBeforeCommit.binarySearch { it.key compareTo key }
        if (index >= 0) {
            checksBeforeCommit[index] = check
        } else {
            checksBeforeCommit.add(index * -1 - 1, check)
        }
    }

    private fun getChangeOrNull(columnFamilyHandle: ColumnFamilyHandle, key: ByteArray, offset: Int = 0, length: Int = key.size): ChangeAction? {
        val columnChanges = changes[columnFamilyHandle.getID()] ?: return null
        val index = columnChanges.binarySearch { it.key.compareToWithOffsetLength(key, offset, length) }
        return if (index >= 0) columnChanges[index] else null
    }
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
