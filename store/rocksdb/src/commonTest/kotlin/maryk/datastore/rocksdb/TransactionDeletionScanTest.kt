package maryk.datastore.rocksdb

import kotlinx.coroutines.test.runTest
import maryk.createTestDBFolder
import maryk.deleteFolder
import maryk.rocksdb.ColumnFamilyHandle
import maryk.rocksdb.FlushOptions
import maryk.test.models.Log
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TransactionDeletionScanTest {
    @Test
    fun rangeDeletionCrossesRecordPrefixesAndIncludesPendingWrites() = runTest {
        val folder = createTestDBFolder("range-deletion-total-order")
        val store = RocksDBDataStore.open(relativePath = folder, dataModelsById = mapOf(1u to Log))
        try {
            val table = store.getColumnFamilies(1u).table
            val keys = (0..4).map { recordKey(it) }
            keys.forEach { store.db.put(table, it, byteArrayOf(1)) }
            FlushOptions().setWaitForFlush(true).use { store.db.flush(it, table) }
            store.createReadContext().use { context ->
                context.withAccessor(store) { accessor ->
                    accessor.getIterator(context.sequentialReadOptions, table).use { iterator ->
                        iterator.seek(keys.first())
                        for (key in keys) {
                            assertTrue(iterator.isValid())
                            assertContentEquals(key, iterator.key())
                            iterator.next()
                        }
                        iterator.seekForPrev(keys.last())
                        for (key in keys.reversed()) {
                            assertTrue(iterator.isValid())
                            assertContentEquals(key, iterator.key())
                            iterator.prev()
                        }
                    }
                }
            }
            Transaction(store).use { transaction ->
                val pendingKey = recordKey(2) + byteArrayOf(1)
                transaction.put(table, pendingKey, byteArrayOf(2))
                transaction.setSavePoint()
                transaction.deleteRange(table, keys[1], keys[4])
                assertNull(transaction.get(table, store.defaultReadOptions, pendingKey))
                for (index in 1..3) assertNull(transaction.get(table, store.defaultReadOptions, keys[index]))
                assertContentEquals(byteArrayOf(1), transaction.get(table, store.defaultReadOptions, keys[0]))
                assertContentEquals(byteArrayOf(1), transaction.get(table, store.defaultReadOptions, keys[4]))
                transaction.rollbackToSavePoint()
                assertContentEquals(byteArrayOf(2), transaction.get(table, store.defaultReadOptions, pendingKey))
                transaction.deleteRange(table, keys[1], keys[4])
                transaction.commit()
                assertNull(store.db.get(table, pendingKey))
                for (index in 1..3) assertNull(store.db.get(table, keys[index]))
                assertContentEquals(byteArrayOf(1), store.db.get(table, keys[4]))
            }
        } finally {
            store.close()
            deleteFolder(folder)
        }
    }

    @Test
    fun shortPrefixDeletionSpansBatchesAndHistoricComparator() = runTest {
        val folder = createTestDBFolder("prefix-deletion-total-order")
        val store = RocksDBDataStore.open(relativePath = folder, dataModelsById = mapOf(1u to Log), keepAllVersions = true)
        try {
            val families = store.getColumnFamilies(1u) as HistoricTableColumnFamilies
            checkPrefixDeletion(store, families.table, historic = false)
            checkPrefixDeletion(store, families.historic.table, historic = true)
        } finally {
            store.close()
            deleteFolder(folder)
        }
    }

    private fun checkPrefixDeletion(store: RocksDBDataStore, table: ColumnFamilyHandle, historic: Boolean) {
        fun key(index: Int) = recordKey(index, prefix = 1) + if (historic) ByteArray(8) else byteArrayOf()
        val outside = recordKey(0, prefix = 2) + if (historic) ByteArray(8) else byteArrayOf()
        repeat(1030) { store.db.put(table, key(it), byteArrayOf(1)) }
        store.db.put(table, outside, byteArrayOf(2))
        FlushOptions().setWaitForFlush(true).use { store.db.flush(it, table) }
        Transaction(store).use { transaction ->
            val pending = key(1030)
            transaction.put(table, pending, byteArrayOf(3))
            transaction.deletePrefix(table, byteArrayOf(1))
            assertNull(transaction.get(table, store.defaultReadOptions, pending))
            transaction.commit()
        }
        repeat(1031) { assertNull(store.db.get(table, key(it))) }
        assertContentEquals(byteArrayOf(2), store.db.get(table, outside))
        store.createReadContext().use { context ->
            assertTrue(context.sequentialReadOptions.totalOrderSeek())
            context.withAccessor(store) { accessor ->
                accessor.getIterator(context.sequentialReadOptions, table).use { iterator ->
                    iterator.seekToFirst()
                    assertTrue(iterator.isValid())
                    assertContentEquals(outside, iterator.key())
                }
            }
        }
    }

    private fun recordKey(index: Int, prefix: Byte = 0) = ByteArray(Log.Meta.keyByteSize).also {
        it[0] = prefix
        it[it.lastIndex - 1] = (index shr 8).toByte()
        it[it.lastIndex] = index.toByte()
    }
}
