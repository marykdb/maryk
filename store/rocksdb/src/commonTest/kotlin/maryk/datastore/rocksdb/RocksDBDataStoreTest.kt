package maryk.datastore.rocksdb

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import maryk.datastore.test.dataModelsForTests
import maryk.datastore.test.runDataStoreTests
import maryk.test.models.SimpleMarykModel
import maryk.deleteFolder
import maryk.createTestDBFolder
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RocksDBDataStoreTest {
    @Test
    fun testDataStore() = runTest {
        val folder = createTestDBFolder("no-history")

        val dataStore = RocksDBDataStore.open(
            relativePath = folder,
            dataModelsById = dataModelsForTests,
            keepAllVersions = false,
        )
        try {
            runDataStoreTests(dataStore)
        } finally {
            dataStore.close()
            deleteFolder(folder)
        }
    }

    @Test
    fun testDataStoreWithKeepAllVersions() = runTest {
        val folder = createTestDBFolder("history")

        val dataStore = RocksDBDataStore.open(
            relativePath = folder,
            keepAllVersions = true,
            dataModelsById = dataModelsForTests,
        )
        try {
            runDataStoreTests(dataStore)
        } finally {
            dataStore.close()
            deleteFolder(folder)
        }
    }

    @Test
    fun testDataStoreWithUpdateHistoryIndex() = runTest {
        val folder = createTestDBFolder("update-history")

        val dataStore = RocksDBDataStore.open(
            relativePath = folder,
            keepAllVersions = true,
            keepUpdateHistoryIndex = true,
            dataModelsById = dataModelsForTests,
        )
        try {
            runDataStoreTests(dataStore, "executeSimpleScanUpdatesRequestWithUpdateHistoryIndex")
            runDataStoreTests(dataStore, "executeHistoryStyleScanUpdatesRequestFallsBackWithoutUpdateHistoryIndex")
            runDataStoreTests(dataStore, "executeScanUpdateHistoryReturnsVersionOrderedEntries")
        } finally {
            dataStore.close()
            deleteFolder(folder)
        }
    }

    @Test
    fun closeIsIdempotent() = runTest {
        val folder = createTestDBFolder("close-idempotent")

        val dataStore = RocksDBDataStore.open(
            relativePath = folder,
            dataModelsById = dataModelsForTests,
        )
        try {
            dataStore.close()
            dataStore.close()
        } finally {
            deleteFolder(folder)
        }
    }

    @Test
    fun closeFailsPendingMigrationWaiters() = runTest {
        val folder = createTestDBFolder("close-pending-migration-waiter")

        val dataStore = RocksDBDataStore.open(
            relativePath = folder,
            dataModelsById = dataModelsForTests,
        )
        try {
            val waiter = dataStore.ensurePendingMigrationWaiter(1u)
            dataStore.close()

            assertFailsWith<CancellationException> {
                waiter.await()
            }
        } finally {
            dataStore.close()
            deleteFolder(folder)
        }
    }

    @Test
    fun transactionIteratorReverseSeekHandlesPendingOnlyColumnFamily() = runTest {
        val folder = createTestDBFolder("transaction-iterator-pending-only")

        val dataStore = RocksDBDataStore.open(
            relativePath = folder,
            dataModelsById = dataModelsForTests,
        )
        try {
            val columnFamilies = dataStore.getColumnFamilies(SimpleMarykModel)
            val transaction = Transaction(dataStore)
            transaction.put(columnFamilies.keys, byteArrayOf(1), byteArrayOf(11))

            transaction.getIterator(dataStore.defaultReadOptions, columnFamilies.keys).use { iterator ->
                iterator.seekToLast()
                assertTrue(iterator.isValid())
                assertContentEquals(byteArrayOf(1), iterator.key())

                iterator.seekForPrev(byteArrayOf(0))
                assertFalse(iterator.isValid())
            }
        } finally {
            dataStore.close()
            deleteFolder(folder)
        }
    }

    @Test
    fun transactionGetWithBufferReturnsLengthWhenPendingValueDoesNotFit() = runTest {
        val folder = createTestDBFolder("transaction-get-large-pending")

        val dataStore = RocksDBDataStore.open(
            relativePath = folder,
            dataModelsById = dataModelsForTests,
        )
        try {
            val columnFamilies = dataStore.getColumnFamilies(SimpleMarykModel)
            val transaction = Transaction(dataStore)
            val key = byteArrayOf(1)
            val value = byteArrayOf(2, 3, 4, 5)
            val buffer = ByteArray(2)

            transaction.put(columnFamilies.keys, key, value)

            assertEquals(value.size, transaction.get(columnFamilies.keys, dataStore.defaultReadOptions, key, buffer))
            assertContentEquals(byteArrayOf(2, 3), buffer)
        } finally {
            dataStore.close()
            deleteFolder(folder)
        }
    }

    @Test
    fun transactionIteratorReverseWalkIncludesFirstPendingChangeAfterCommittedKey() = runTest {
        val folder = createTestDBFolder("transaction-iterator-reverse-first-pending")

        val dataStore = RocksDBDataStore.open(
            relativePath = folder,
            dataModelsById = dataModelsForTests,
        )
        try {
            val columnFamilies = dataStore.getColumnFamilies(SimpleMarykModel)
            dataStore.db.put(columnFamilies.keys, byteArrayOf(2), byteArrayOf(22))

            val transaction = Transaction(dataStore)
            transaction.put(columnFamilies.keys, byteArrayOf(1), byteArrayOf(11))

            transaction.getIterator(dataStore.defaultReadOptions, columnFamilies.keys).use { iterator ->
                iterator.seekToLast()
                assertTrue(iterator.isValid())
                assertContentEquals(byteArrayOf(2), iterator.key())

                iterator.prev()
                assertTrue(iterator.isValid())
                assertContentEquals(byteArrayOf(1), iterator.key())
            }
        } finally {
            dataStore.close()
            deleteFolder(folder)
        }
    }
}
