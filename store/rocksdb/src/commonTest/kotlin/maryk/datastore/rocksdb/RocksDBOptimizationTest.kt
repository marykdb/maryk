package maryk.datastore.rocksdb

import kotlinx.coroutines.test.runTest
import maryk.core.exceptions.StorageException
import maryk.core.properties.types.Key
import maryk.createTestDBFolder
import maryk.datastore.rocksdb.processors.helpers.checkExistence
import maryk.deleteFolder
import maryk.rocksdb.DBOptions
import maryk.rocksdb.FlushOptions
import maryk.rocksdb.ReadOptions
import maryk.rocksdb.ReadTier
import maryk.rocksdb.RocksDBException
import maryk.test.models.Log
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RocksDBOptimizationTest {
    @Test
    fun boundedIterationPreservesSnapshotsAndPendingWrites() = runTest {
        val folder = createTestDBFolder("bounded-snapshot")
        val store = RocksDBDataStore.open(relativePath = folder, dataModelsById = mapOf(1u to Log))
        try {
            val family = store.getColumnFamilies(1u).model
            val first = byteArrayOf(-1, 0)
            val second = byteArrayOf(-1, 1)
            store.db.put(family, first, byteArrayOf(1))
            store.createReadContext().use { context ->
                store.db.put(family, second, byteArrayOf(2))
                context.withAccessor(store) { accessor ->
                    accessor.withBoundedIterator(context.defaultReadOptions, family, first, second) { iterator ->
                        iterator.seekToLast()
                        assertTrue(iterator.isValid())
                        assertContentEquals(first, iterator.key())
                        iterator.next()
                        assertFalse(iterator.isValid())
                    }
                }
            }
            Transaction(store).use { transaction ->
                val pending = byteArrayOf(-1, 2)
                transaction.put(family, pending, byteArrayOf(3))
                transaction.withBoundedIterator(store.defaultReadOptions, family, second, bytewisePrefixLimit(byteArrayOf(-1))) { iterator ->
                    iterator.seekToFirst()
                    assertContentEquals(second, iterator.key())
                    iterator.next()
                    assertContentEquals(pending, iterator.key())
                }
            }
            assertEquals(null, bytewisePrefixLimit(byteArrayOf(-1)))
            assertContentEquals(byteArrayOf(2), bytewisePrefixLimit(byteArrayOf(1, -1)))
        } finally {
            store.close()
            deleteFolder(folder)
        }
    }

    @Test
    fun iteratorReadFailuresPropagateAndExhaustedExistenceCheckStops() = runTest {
        val folder = createTestDBFolder("iterator-status")
        var store = RocksDBDataStore.open(relativePath = folder, dataModelsById = mapOf(1u to Log))
        try {
            var family = store.getColumnFamilies(1u).keys
            val key = ByteArray(Log.Meta.keyByteSize)
            store.db.put(family, key, ByteArray(8192))
            FlushOptions().setWaitForFlush(true).use { store.db.flush(it, family) }
            store.close()
            store = RocksDBDataStore.open(relativePath = folder, dataModelsById = mapOf(1u to Log))
            family = store.getColumnFamilies(1u).keys
            // A cold cache-only read must report Incomplete rather than an empty scan.
            ReadOptions().setTotalOrderSeek(true).setReadTier(ReadTier.BLOCK_CACHE_TIER).use { options ->
                assertFailsWith<RocksDBException> {
                    DBAccessor(store).getIterator(options, family).use { iterator ->
                        iterator.seekToFirst()
                        iterator.isValid()
                    }
                }
            }
            DBAccessor(store).getIterator(store.maintenanceReadOptions, family).use { iterator ->
                iterator.seekToFirst()
                val missing = Key<Log>(ByteArray(Log.Meta.keyByteSize) { 1 })
                assertFailsWith<StorageException> { checkExistence(iterator, missing) }
            }
        } finally {
            store.close()
            deleteFolder(folder)
        }
    }
}
