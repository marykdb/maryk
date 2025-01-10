package maryk.datastore.rocksdb

import maryk.datastore.rocksdb.ChangeAction.Delete
import maryk.datastore.rocksdb.ChangeAction.Put
import maryk.lib.extensions.compare.compareDefinedTo
import maryk.lib.extensions.compare.compareTo
import maryk.rocksdb.ColumnFamilyHandle
import maryk.rocksdb.RocksDBException
import maryk.rocksdb.RocksIterator
import kotlin.math.max

class TransactionIterator(
    private val transaction: Transaction,
    private val columnFamilyHandle: ColumnFamilyHandle,
    rocksIterator: RocksIterator
): DBIterator(rocksIterator) {
    private var changesIndex: Int = -1
    private var lastChangesIndex: Int = -1
    private var fromChanges: Boolean = false
    private var startPrefix: ByteArray? = null

    private fun transactionChanges() = transaction.changes.getOrPut(columnFamilyHandle.getID()) { mutableListOf() }

    override fun seek(target: ByteArray) {
        super.seek(target)

        val changes = transactionChanges()

        val index = changes.binarySearch {
            it.key compareTo target
        }

        changesIndex = if (index >= 0) index else -index - 1
        fromChanges = !rocksIterator.isValid() ||
            (changes.isNotEmpty()
                && changesIndex < changes.size
                && rocksIterator.key() >= changes[changesIndex].key
            )
        startPrefix = target.copyOfRange(0, transaction.rocksDBDataStore.getPrefixSize(columnFamilyHandle))

        // Skip to end if prefix does not match
        if (changesIndex < changes.size && target.compareDefinedTo(changes[changesIndex].key) != 0) {
            lastChangesIndex = changesIndex
            changesIndex = changes.size
        }
    }

    override fun seekForPrev(target: ByteArray) {
        super.seekForPrev(target)

        val changes = transactionChanges()

        val index = changes.binarySearch {
            it.key compareTo target
        }

        changesIndex = if (index >= 0) index else -index - 2
        fromChanges = (changes.isNotEmpty() && changesIndex < changes.size && rocksIterator.key() <= transactionChanges()[changesIndex].key)
        startPrefix = target.copyOfRange(0, transaction.rocksDBDataStore.getPrefixSize(columnFamilyHandle))

        // Skip to start if prefix does not match
        if (changesIndex >= 0 && target.compareDefinedTo(changes[changesIndex].key) != 0) {
            lastChangesIndex = changesIndex
            changesIndex = -1
        }
    }

    override fun seekToLast() {
        super.seekToLast()

        val changes = transactionChanges()

        changesIndex = changes.lastIndex
        fromChanges = changes.isNotEmpty() && rocksIterator.key() <= changes.last().key
    }

    override fun isValid() =
        super.isValid() && if (fromChanges) transactionChanges().getOrNull(changesIndex) != null else true

    override fun next() {
        val changes = transactionChanges()
        if (fromChanges) {
            // If order of walking was reversed and end was reached before, restore it
            if (changesIndex == -1) {
                changesIndex = lastChangesIndex
            }

            // Also move rocks iterator if equal
            if (changes[changesIndex].key.contentEquals(super.key())) {
                super.next()
            }

            changesIndex++
            // Skip to end if prefix does not match
            if (changesIndex < changes.size && startPrefix != null && startPrefix!!.compareDefinedTo(changes[changesIndex].key) != 0) {
                lastChangesIndex = changesIndex
                changesIndex = changes.size
            }
        } else {
            // Skip all changes that were added in the meantime
            if (changes.isNotEmpty()) {
                while (changesIndex < changes.size && rocksIterator.key() >= changes[changesIndex].key) {
                    changesIndex++
                }
            }

            super.next()
        }
        fromChanges = changes.isNotEmpty() && changesIndex < changes.size && (!rocksIterator.isValid() || rocksIterator.key() >= changes[changesIndex].key)

        if (fromChanges) {
            changes.getOrNull(changesIndex)?.let {
                if (it is Delete) {
                    // Skip since was deleted
                    this.next()
                }
            }
        }
    }

    override fun prev() {
        val changes = transactionChanges()
        if (fromChanges) {
            // If order of walking was reversed and end was reached before, restore it
            if (changesIndex == changes.size) {
                changesIndex = max(0, lastChangesIndex)
            }

            // Also move rocksiterator if equal
            if (changes[changesIndex].key.contentEquals(super.key())) {
                super.prev()
            }

            changesIndex--
            // Skip to start if prefix does not match
            if (changesIndex >= 0 && startPrefix != null && startPrefix!!.compareDefinedTo(changes[changesIndex].key) != 0) {
                lastChangesIndex = changesIndex
                changesIndex = -1
            }
        } else {
            // Skip all changes that were added in the meantime
            if (changes.isNotEmpty()) {
                while (changesIndex > -1 && rocksIterator.key() <= changes[changesIndex].key) {
                    changesIndex--
                }
            }

            super.prev()
        }
        fromChanges = changes.isNotEmpty() && changesIndex > 0 && (!rocksIterator.isValid() || rocksIterator.key() <= changes[changesIndex].key)

        if (fromChanges) {
            changes.getOrNull(changesIndex)?.let {
                if (it is Delete) {
                    // Skip since was deleted
                    this.prev()
                }
            }
        }
    }

    override fun key() =
        if (fromChanges) {
            transactionChanges().getOrElse(changesIndex) {
                throw RocksDBException("Invalid key")
            }.key
        } else {
            super.key()
        }

    override fun value() =
        if (fromChanges) {
            val change = transactionChanges().getOrElse(changesIndex) {
                throw RocksDBException("Invalid key")
            }
            when (change) {
               is Put -> change.value
               is Delete -> throw RocksDBException("Deleted value")
            }
        } else {
            super.value()
        }
}
