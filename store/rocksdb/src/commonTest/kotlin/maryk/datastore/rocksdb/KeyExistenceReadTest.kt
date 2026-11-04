package maryk.datastore.rocksdb

import kotlinx.coroutines.test.runTest
import maryk.createTestDBFolder
import maryk.datastore.shared.AtomicAbort
import maryk.datastore.shared.MAX_ATOMIC_WORK_BYTES
import maryk.deleteFolder
import maryk.rocksdb.RocksDBException
import maryk.test.models.Log
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class KeyExistenceReadTest {
    @Test
    fun exactExistenceHandlesEmptyValuesAndRetainsSnapshotVisibility() = runTest {
        val folder = createTestDBFolder("key-exists-snapshot")
        val store = RocksDBDataStore.open(relativePath = folder, dataModelsById = mapOf(1u to Log))
        try {
            val family = store.getColumnFamilies(1u).model
            val empty = byteArrayOf(90, 1)
            val present = byteArrayOf(90, 2)
            val missing = byteArrayOf(90, 3)
            store.db.put(family, empty, byteArrayOf())
            store.db.put(family, present, ByteArray(4096))
            store.createReadContext().use { context ->
                store.db.delete(family, empty)
                store.db.put(family, missing, byteArrayOf())
                context.withAccessor(store) { accessor ->
                    assertTrue(accessor.keyExists(family, context.defaultReadOptions, empty))
                    assertTrue(accessor.keyExists(family, context.defaultReadOptions, present))
                    assertFalse(accessor.keyExists(family, context.defaultReadOptions, missing))
                    assertFalse(accessor.keyExists(family, context.defaultReadOptions, byteArrayOf(90)))
                }
            }
            DBAccessor(store).use { accessor ->
                assertFalse(accessor.keyExists(family, store.defaultReadOptions, empty))
                assertTrue(accessor.keyExists(family, store.defaultReadOptions, missing))
            }
        } finally {
            store.close()
            deleteFolder(folder)
        }
    }

    @Test
    fun transactionalExistenceSeesWritesAndTracksPresentAndAbsentConflicts() = runTest {
        val folder = createTestDBFolder("key-exists-transaction")
        val store = RocksDBDataStore.open(relativePath = folder, dataModelsById = mapOf(1u to Log))
        try {
            val family = store.getColumnFamilies(1u).model
            val pending = byteArrayOf(90, 1)
            val deleted = byteArrayOf(90, 2)
            val missing = byteArrayOf(90, 3)
            val present = byteArrayOf(90, 4)
            for (conflictKey in listOf(missing, present)) {
                store.db.put(family, pending, byteArrayOf(1))
                store.db.put(family, deleted, byteArrayOf(1))
                store.db.put(family, present, byteArrayOf(1))
                store.db.delete(family, missing)
                Transaction(store).use { transaction ->
                    transaction.put(family, pending, byteArrayOf())
                    transaction.delete(family, deleted)
                    for (atomic in listOf(false, true)) {
                        transaction.atomicOwner = atomic
                        assertTrue(transaction.keyExists(family, store.defaultReadOptions, pending))
                        assertFalse(transaction.keyExists(family, store.defaultReadOptions, deleted))
                        assertFalse(transaction.keyExists(family, store.defaultReadOptions, missing))
                        assertTrue(transaction.keyExists(family, store.defaultReadOptions, present))
                    }
                    store.db.put(family, conflictKey, byteArrayOf(2))
                    transaction.atomicOwner = false
                    assertFailsWith<RocksDBException> { transaction.commit() }
                }
                assertContentEquals(byteArrayOf(1), store.db.get(family, pending))
                assertContentEquals(byteArrayOf(1), store.db.get(family, deleted))
            }
        } finally {
            store.close()
            deleteFolder(folder)
        }
    }

    @Test
    fun transactionalExistenceRetainsAtomicWorkAccounting() = runTest {
        val folder = createTestDBFolder("key-exists-work-budget")
        val store = RocksDBDataStore.open(relativePath = folder, dataModelsById = mapOf(1u to Log))
        try {
            val family = store.getColumnFamilies(1u).model
            val key = byteArrayOf(90)
            store.db.put(family, key, ByteArray(MAX_ATOMIC_WORK_BYTES + 1))
            Transaction(store).use { transaction ->
                transaction.atomicOwner = true
                assertFailsWith<AtomicAbort> { transaction.keyExists(family, store.defaultReadOptions, key) }
            }
        } finally {
            store.close()
            deleteFolder(folder)
        }
    }
}
