package maryk.datastore.rocksdb

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import maryk.core.clock.HLC
import maryk.core.query.changes.Change
import maryk.core.query.changes.MultiTypeChange
import maryk.core.query.pairs.with
import maryk.core.properties.types.Key
import maryk.datastore.rocksdb.processors.deleteCompleteIndexContents
import maryk.datastore.rocksdb.processors.helpers.createIndexKey
import maryk.datastore.rocksdb.processors.processChange
import maryk.datastore.test.UniqueModel
import maryk.datastore.test.UniqueModel.email
import maryk.datastore.test.assertStatusIs
import maryk.datastore.test.dataModelsForTests
import maryk.datastore.test.runDataStoreTests
import maryk.test.models.SimpleMarykModel
import maryk.test.models.TestMarykModel
import maryk.deleteFolder
import maryk.createTestDBFolder
import maryk.core.query.requests.add
import maryk.core.query.requests.delete
import maryk.core.query.requests.get
import maryk.core.query.responses.statuses.AddSuccess
import maryk.core.query.responses.statuses.DeleteSuccess
import maryk.core.query.responses.statuses.ServerFail
import maryk.core.query.responses.statuses.ValidationFail
import maryk.rocksdb.RocksDBException
import maryk.rocksdb.rocksDBNotFound
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RocksDBDataStoreTest {
    @Test
    fun opensWithNativeOptimisticTransactionSupport() = runTest {
        val folder = createTestDBFolder("optimistic-transaction-db")

        val dataStore = RocksDBDataStore.open(
            relativePath = folder,
            dataModelsById = dataModelsForTests,
        )
        try {
            assertEquals("OptimisticTransactionDB", dataStore.db::class.simpleName)
        } finally {
            dataStore.close()
            deleteFolder(folder)
        }
    }

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
            runDataStoreTests(dataStore, "executeScanValuesAsFlowRequestWithUpdateHistoryIndexRefill")
            runDataStoreTests(dataStore, "executeScanUpdateHistoryReturnsVersionOrderedEntries")
            runDataStoreTests(dataStore, "executeScanUpdateHistoryCanIncludeSoftDeleteAtHistoricVersion")
            runDataStoreTests(dataStore, "executeScanUpdatesAsFlowRequestWithUpdateHistoryIndex")
            runDataStoreTests(dataStore, "executeScanUpdatesAsFlowRequestWithUpdateHistoryIndexTracksNewTopKey")
            runDataStoreTests(dataStore, "executeScanUpdatesAsFlowRequestWithUpdateHistoryIndexStartKey")
            runDataStoreTests(dataStore, "executeScanUpdatesAsFlowRequestWithUpdateHistoryIndexRefillsAfterDeletion")
        } finally {
            dataStore.close()
            deleteFolder(folder)
        }
    }

    @Test
    fun testOrderedScanFlowUpdatesSortedValueWhenPositionStaysSame() = runTest {
        val folder = createTestDBFolder("any-value-flow-sorted-value")

        val dataStore = RocksDBDataStore.open(
            relativePath = folder,
            dataModelsById = dataModelsForTests,
            keepAllVersions = false,
        )
        try {
            runDataStoreTests(dataStore, "executeOrderedScanFlowUpdatesSortedValueWhenPositionStaysSame")
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
    fun failedDuplicateAddDoesNotPersistStagedObject() = runTest {
        val folder = createTestDBFolder("duplicate-add-rollback")

        val dataStore = RocksDBDataStore.open(
            relativePath = folder,
            dataModelsById = dataModelsForTests,
        )
        try {
            val firstKey = Key<UniqueModel>(ByteArray(16) { 1 })
            val duplicateKey = Key<UniqueModel>(ByteArray(16) { 2 })
            val addResponse = dataStore.execute(
                UniqueModel.add(
                    firstKey to UniqueModel.create { email with "dup@test.com" },
                    duplicateKey to UniqueModel.create { email with "dup@test.com" },
                )
            )

            assertStatusIs<AddSuccess<UniqueModel>>(addResponse.statuses[0])
            assertStatusIs<ValidationFail<UniqueModel>>(addResponse.statuses[1])

            val getResponse = dataStore.execute(UniqueModel.get(duplicateKey))
            assertEquals(0, getResponse.values.size)
        } finally {
            dataStore.close()
            deleteFolder(folder)
        }
    }

    @Test
    fun unsupportedChangeDoesNotCommitPreviouslyStagedWrites() = runTest {
        val folder = createTestDBFolder("failed-change-rollback")

        val dataStore = RocksDBDataStore.open(
            relativePath = folder,
            dataModelsById = dataModelsForTests,
        )
        try {
            val key = Key<UniqueModel>(ByteArray(16) { 1 })
            dataStore.execute(
                UniqueModel.add(key to UniqueModel.create { email with "before@test.com" })
            ).statuses.forEach { assertStatusIs<AddSuccess<UniqueModel>>(it) }

            val transaction = Transaction(dataStore)
            val status = dataStore.processChange(
                dataModel = UniqueModel,
                columnFamilies = dataStore.getColumnFamilies(UniqueModel),
                key = key,
                lastVersion = null,
                changes = listOf(
                    Change(UniqueModel.email.ref() with "after@test.com"),
                    MultiTypeChange(),
                ),
                transaction = transaction,
                dbIndex = dataStore.getDataModelId(UniqueModel),
                version = HLC(2uL),
            )
            assertStatusIs<ServerFail<UniqueModel>>(status)
            transaction.commit()

            val getResponse = dataStore.execute(UniqueModel.get(key))
            assertEquals("before@test.com", getResponse.values.first().values { email })
        } finally {
            dataStore.close()
            deleteFolder(folder)
        }
    }

    @Test
    fun uniqueIndexDefinitionsAreFoundAfterReopenForHardDeleteCleanup() = runTest {
        val folder = createTestDBFolder("unique-definitions-reopen")

        var dataStore = RocksDBDataStore.open(
            relativePath = folder,
            dataModelsById = dataModelsForTests,
        )
        val key = Key<UniqueModel>(ByteArray(16) { 1 })
        try {
            dataStore.execute(
                UniqueModel.add(key to UniqueModel.create { email with "reuse@test.com" })
            ).statuses.forEach { assertStatusIs<AddSuccess<UniqueModel>>(it) }
        } finally {
            dataStore.close()
        }

        dataStore = RocksDBDataStore.open(
            relativePath = folder,
            dataModelsById = dataModelsForTests,
        )
        try {
            dataStore.execute(UniqueModel.delete(key, hardDelete = true))
                .statuses
                .forEach { assertStatusIs<DeleteSuccess<UniqueModel>>(it) }

            val addAgain = dataStore.execute(
                UniqueModel.add(Key<UniqueModel>(ByteArray(16) { 2 }) to UniqueModel.create { email with "reuse@test.com" })
            )
            assertStatusIs<AddSuccess<UniqueModel>>(addAgain.statuses.first())
        } finally {
            dataStore.close()
            deleteFolder(folder)
        }
    }

    @Test
    fun hardDeleteCleanupFallsBackToModelUniqueDefinitionsWhenMarkerIsMissing() = runTest {
        val folder = createTestDBFolder("unique-definitions-missing-marker")

        var dataStore = RocksDBDataStore.open(
            relativePath = folder,
            dataModelsById = dataModelsForTests,
        )
        val key = Key<UniqueModel>(ByteArray(16) { 3 })
        val uniqueDefinitionMarker = byteArrayOf(0) + UniqueModel { email::ref }.toStorageByteArray()
        try {
            dataStore.execute(
                UniqueModel.add(key to UniqueModel.create { email with "markerless@test.com" })
            ).statuses.forEach { assertStatusIs<AddSuccess<UniqueModel>>(it) }

            val uniqueHandle = dataStore.getColumnFamilies(UniqueModel).unique
            dataStore.db.delete(uniqueHandle, uniqueDefinitionMarker)
        } finally {
            dataStore.close()
        }

        dataStore = RocksDBDataStore.open(
            relativePath = folder,
            dataModelsById = dataModelsForTests,
        )
        try {
            dataStore.execute(UniqueModel.delete(key, hardDelete = true))
                .statuses
                .forEach { assertStatusIs<DeleteSuccess<UniqueModel>>(it) }

            val addAgain = dataStore.execute(
                UniqueModel.add(Key<UniqueModel>(ByteArray(16) { 4 }) to UniqueModel.create { email with "markerless@test.com" })
            )
            assertStatusIs<AddSuccess<UniqueModel>>(addAgain.statuses.first())
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
    fun transactionGetWithBufferDoesNotMutateBufferWhenKeyIsMissing() = runTest {
        val folder = createTestDBFolder("transaction-get-missing-buffer")

        val dataStore = RocksDBDataStore.open(
            relativePath = folder,
            dataModelsById = dataModelsForTests,
        )
        try {
            val columnFamilies = dataStore.getColumnFamilies(SimpleMarykModel)
            val transaction = Transaction(dataStore)
            val buffer = byteArrayOf(7, 8, 9, 10)

            assertEquals(
                rocksDBNotFound,
                transaction.get(
                    columnFamilies.keys,
                    dataStore.defaultReadOptions,
                    byteArrayOf(99),
                    0,
                    1,
                    buffer,
                    1,
                    2
                )
            )
            assertContentEquals(byteArrayOf(7, 8, 9, 10), buffer)
        } finally {
            dataStore.close()
            deleteFolder(folder)
        }
    }

    @Test
    fun transactionSavePointRollbackRemovesPendingWritesAfterSavePoint() = runTest {
        val folder = createTestDBFolder("transaction-savepoint-rollback")

        val dataStore = RocksDBDataStore.open(
            relativePath = folder,
            dataModelsById = dataModelsForTests,
        )
        try {
            val columnFamilies = dataStore.getColumnFamilies(SimpleMarykModel)
            val transaction = Transaction(dataStore)

            transaction.put(columnFamilies.keys, byteArrayOf(1), byteArrayOf(11))
            transaction.setSavePoint()
            transaction.put(columnFamilies.keys, byteArrayOf(2), byteArrayOf(22))
            transaction.rollbackToSavePoint()
            transaction.commit()

            assertContentEquals(byteArrayOf(11), dataStore.db.get(columnFamilies.keys, dataStore.defaultReadOptions, byteArrayOf(1)))
            assertEquals(null, dataStore.db.get(columnFamilies.keys, dataStore.defaultReadOptions, byteArrayOf(2)))
        } finally {
            dataStore.close()
            deleteFolder(folder)
        }
    }

    @Test
    fun optimisticTransactionCommitDetectsWriteConflict() = runTest {
        val folder = createTestDBFolder("transaction-write-conflict")

        val dataStore = RocksDBDataStore.open(
            relativePath = folder,
            dataModelsById = dataModelsForTests,
        )
        try {
            val columnFamilies = dataStore.getColumnFamilies(SimpleMarykModel)
            val first = Transaction(dataStore)
            val second = Transaction(dataStore)

            first.put(columnFamilies.keys, byteArrayOf(1), byteArrayOf(11))
            second.put(columnFamilies.keys, byteArrayOf(1), byteArrayOf(22))
            second.commit()

            assertFailsWith<RocksDBException> {
                first.commit()
            }
        } finally {
            dataStore.close()
            deleteFolder(folder)
        }
    }

    @Test
    fun deleteCompleteIndexContentsIsInvisibleUntilTransactionCommit() = runTest {
        val folder = createTestDBFolder("transaction-index-clear")

        val dataStore = RocksDBDataStore.open(
            relativePath = folder,
            dataModelsById = dataModelsForTests,
        )
        try {
            val columnFamilies = dataStore.getColumnFamilies(TestMarykModel)
            val index = TestMarykModel.Meta.indexes!!.first()
            val key = createIndexKey(index.referenceStorageByteArray.bytes, byteArrayOf(1, 2, 3))
            dataStore.db.put(columnFamilies.index, key, byteArrayOf(44))

            val transaction = Transaction(dataStore)
            deleteCompleteIndexContents(transaction, columnFamilies, index)

            assertContentEquals(byteArrayOf(44), dataStore.db.get(columnFamilies.index, dataStore.defaultReadOptions, key))
            assertEquals(null, transaction.get(columnFamilies.index, dataStore.defaultReadOptions, key))

            transaction.commit()

            assertEquals(null, dataStore.db.get(columnFamilies.index, dataStore.defaultReadOptions, key))
        } finally {
            dataStore.close()
            deleteFolder(folder)
        }
    }

    @Test
    fun transactionDeleteRangeClearsLargeRangeInBatches() = runTest {
        val folder = createTestDBFolder("transaction-delete-range-large")

        val dataStore = RocksDBDataStore.open(
            relativePath = folder,
            dataModelsById = dataModelsForTests,
        )
        try {
            val columnFamilies = dataStore.getColumnFamilies(SimpleMarykModel)
            repeat(1500) { index ->
                val key = byteArrayOf(1, (index / 256).toByte(), index.toByte())
                dataStore.db.put(columnFamilies.keys, key, byteArrayOf(index.toByte()))
            }
            dataStore.db.put(columnFamilies.keys, byteArrayOf(2, 0), byteArrayOf(99))

            val transaction = Transaction(dataStore)
            transaction.deleteRange(columnFamilies.keys, byteArrayOf(1), byteArrayOf(2))

            assertEquals(null, transaction.get(columnFamilies.keys, dataStore.defaultReadOptions, byteArrayOf(1, 0, 0)))
            assertContentEquals(byteArrayOf(0), dataStore.db.get(columnFamilies.keys, dataStore.defaultReadOptions, byteArrayOf(1, 0, 0)))
            assertContentEquals(byteArrayOf(99), transaction.get(columnFamilies.keys, dataStore.defaultReadOptions, byteArrayOf(2, 0)))

            transaction.commit()

            assertEquals(null, dataStore.db.get(columnFamilies.keys, dataStore.defaultReadOptions, byteArrayOf(1, 0, 0)))
            assertEquals(null, dataStore.db.get(columnFamilies.keys, dataStore.defaultReadOptions, byteArrayOf(1, 5, 200.toByte())))
            assertContentEquals(byteArrayOf(99), dataStore.db.get(columnFamilies.keys, dataStore.defaultReadOptions, byteArrayOf(2, 0)))
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
