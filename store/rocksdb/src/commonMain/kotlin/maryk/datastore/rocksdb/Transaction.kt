package maryk.datastore.rocksdb

import maryk.datastore.rocksdb.ChangeAction.Delete
import maryk.datastore.rocksdb.ChangeAction.Put
import maryk.lib.extensions.compare.compareTo
import maryk.lib.extensions.toHex
import org.rocksdb.ColumnFamilyHandle
import org.rocksdb.ReadOptions
import org.rocksdb.RocksDB
import org.rocksdb.RocksDBException

internal sealed class ChangeAction(
    val columnFamilyHandle: ColumnFamilyHandle,
    val key: ByteArray
) {
    class Put(columnFamilyHandle: ColumnFamilyHandle, key: ByteArray, val value: ByteArray) : ChangeAction(columnFamilyHandle, key)
    class Delete(columnFamilyHandle: ColumnFamilyHandle, key: ByteArray) : ChangeAction(columnFamilyHandle, key)
}

private class CheckBeforeCommit(
    val columnFamilyHandle: ColumnFamilyHandle,
    val key: ByteArray,
    val value: ByteArray?
)

class Transaction(val rocksDBDataStore: RocksDBDataStore): DBAccessor(rocksDBDataStore) {
    private var savedChanges: Map<Int, List<ChangeAction>>? = null
    // Changes sorted on the column family ID
    internal val changes = mutableMapOf<Int, MutableList<ChangeAction>>()
    private var checksBeforeCommit: MutableList<CheckBeforeCommit>? = null

    fun put(columnFamilyHandle: ColumnFamilyHandle, key: ByteArray, value: ByteArray) {
        setChange(
            key = key,
            action = Put(columnFamilyHandle, key, value)
        )
    }

    fun delete(columnFamilyHandle: ColumnFamilyHandle, key: ByteArray) {
        setChange(
            key = key,
            action = Delete(columnFamilyHandle, key)
        )
    }

    override fun get(columnFamilyHandle: ColumnFamilyHandle, readOptions: ReadOptions, key: ByteArray): ByteArray? {
        val columnChanges = this.changes.getOrPut(columnFamilyHandle.getID()) { mutableListOf() }
        val index = columnChanges.binarySearch { it.key compareTo key }

        return if (index < 0) {
            dataStore.db.get(columnFamilyHandle, readOptions, key)
        } else when (val change = columnChanges[index]) {
            is Put -> change.value
            is Delete -> null
        }
    }

    override fun get(columnFamilyHandle: ColumnFamilyHandle, readOptions: ReadOptions, key: ByteArray, offset: Int, len: Int, value: ByteArray, vOffset: Int, vLen: Int): Int {
        val columnChanges = this.changes.getOrPut(columnFamilyHandle.getID()) { mutableListOf() }
        val index = columnChanges.binarySearch { it.key compareTo key }

        return if (index < 0) {
            super.get(columnFamilyHandle, readOptions, key, offset, len, value, vOffset, vLen)
        } else when (val change = columnChanges[index]) {
            is Put -> {
                change.value.copyInto(value, vOffset)
                change.value.size
            }
            is Delete -> RocksDB.NOT_FOUND
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
        this.checksBeforeCommit?.forEach { check ->
            val current = dataStore.db.get(check.columnFamilyHandle, check.key)

            if (check.value == null) {
                if (current != null) {
                    throw RocksDBException("Merge error, key ${check.key.toHex()} changed before commit")
                }
            } else if (current == null || check.value compareTo current != 0) {
                throw RocksDBException("Merge error, key ${check.key.toHex()} changed before commit")
            }
        }

        for (changePerFamily in changes) {
            for (change in changePerFamily.value) {
                when (change) {
                    is Put -> dataStore.db.put(change.columnFamilyHandle, change.key, change.value)
                    is Delete -> dataStore.db.delete(change.columnFamilyHandle, change.key)
                }
            }
        }
        this.changes.clear()
        this.savedChanges = null
    }

    fun setSavePoint() {
        this.savedChanges = this.changes.map { (index, value) ->
            Pair(index, value.toList())
        }.toMap()
    }

    fun rollbackToSavePoint() {
        this.changes.clear()
        this.savedChanges?.let { savedChanges ->
            for ((family, changes) in savedChanges) {
                this.changes[family] = changes.toMutableList()
            }
        }
    }

    private fun setChange(key: ByteArray, action: ChangeAction) {
        val columnChanges = this.changes.getOrPut(action.columnFamilyHandle.getID()) { mutableListOf() }
        val index = columnChanges.binarySearch { it.key compareTo key }
        if (index >= 0) {
            columnChanges[index] = action
        } else {
            columnChanges.add(index * -1 - 1, action)
        }
    }

    private fun setCheckBeforeCommit(columnFamilyHandle: ColumnFamilyHandle, key: ByteArray, value: ByteArray?) {
        val checksBeforeCommit = this.checksBeforeCommit ?:
            mutableListOf<CheckBeforeCommit>().also {
                this.checksBeforeCommit = it
            }

        val index = checksBeforeCommit.binarySearch { it.key compareTo key }
        val check = CheckBeforeCommit(columnFamilyHandle, key, value)
        if (index >= 0) {
            checksBeforeCommit[index] = check
        } else {
            checksBeforeCommit.add(index * -1 - 1, check)
        }
    }
}
