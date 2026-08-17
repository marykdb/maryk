package maryk.datastore.indexeddb

import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import maryk.core.exceptions.RequestException
import maryk.core.models.RootDataModel
import maryk.core.models.migration.MigrationConfiguration
import maryk.core.models.migration.MigrationOutcome
import maryk.core.models.migration.MigrationRetryPolicy
import maryk.core.models.migration.MigrationStateStatus
import maryk.core.properties.definitions.fixedBytes
import maryk.core.properties.definitions.string
import maryk.core.properties.types.Bytes
import maryk.core.properties.types.Key
import maryk.core.properties.types.Version
import maryk.core.properties.types.invoke
import maryk.core.query.changes.Change
import maryk.core.query.changes.ObjectSoftDeleteChange
import maryk.core.query.changes.change
import maryk.core.query.filters.Equals
import maryk.core.query.orders.Orders
import maryk.core.query.orders.Order.Companion.descending as descendingOrder
import maryk.core.query.orders.ascending
import maryk.core.query.orders.descending
import maryk.core.query.pairs.with
import maryk.core.query.requests.add
import maryk.core.query.requests.change
import maryk.core.query.requests.delete
import maryk.core.query.requests.get
import maryk.core.query.requests.getUpdates
import maryk.core.query.requests.scan
import maryk.core.query.requests.scanUpdateHistory
import maryk.core.query.requests.scanUpdates
import maryk.core.query.responses.UpdateResponse
import maryk.core.query.responses.statuses.AddSuccess
import maryk.core.query.responses.statuses.ChangeSuccess
import maryk.core.query.responses.statuses.DeleteSuccess
import maryk.core.query.responses.statuses.IsAddResponseStatus
import maryk.core.query.responses.statuses.ValidationFail
import maryk.core.query.responses.updates.AdditionUpdate
import maryk.core.query.responses.updates.ChangeUpdate
import maryk.core.query.responses.updates.OrderedKeysUpdate
import maryk.core.query.responses.updates.RemovalReason.HardDelete
import maryk.core.query.responses.updates.RemovalUpdate
import maryk.datastore.shared.TypeIndicator
import maryk.datastore.shared.encryption.ContextualFieldEncryptionProvider
import maryk.datastore.shared.encryption.FieldEncryptionContext
import maryk.datastore.shared.encryption.FieldEncryptionEnvelope
import maryk.datastore.shared.encryption.SensitiveIndexTokenProvider
import maryk.datastore.indexeddb.processors.createUpdateHistoryRowKey
import maryk.datastore.indexeddb.processors.decodeCurrentSnapshot
import maryk.datastore.indexeddb.processors.decodeHistoricSnapshot
import maryk.datastore.indexeddb.processors.encodeCurrentSnapshot
import maryk.datastore.indexeddb.processors.encodeHistoricSnapshot
import maryk.datastore.indexeddb.processors.createHistoricSnapshotRowKey
import maryk.datastore.indexeddb.processors.createChangeLogRowKey
import maryk.datastore.indexeddb.processors.createHardDeleteHistoryRowKey
import maryk.datastore.indexeddb.processors.createHistoricVersionedRowKey
import maryk.datastore.indexeddb.processors.createUniqueRowKey
import maryk.datastore.indexeddb.processors.hardDeleteHistoryRowReadObserver
import maryk.datastore.indexeddb.processors.toBigEndianBytes
import maryk.datastore.test.DataStoreAddTest
import maryk.datastore.test.DataStoreBackupRoundTripTest
import maryk.lib.bytes.combineToByteArray
import maryk.datastore.test.DataStoreChangeComplexTest
import maryk.datastore.test.DataStoreChangeTest
import maryk.datastore.test.DataStoreChangeValidationTest
import maryk.datastore.test.DataStoreDeleteTest
import maryk.datastore.test.DataStoreFilterComplexTest
import maryk.datastore.test.DataStoreFilterTest
import maryk.datastore.test.DataStoreGetChangesTest
import maryk.datastore.test.DataStoreGetTest
import maryk.datastore.test.DataStoreGetUpdatesAndFlowTest
import maryk.datastore.test.DataStoreGeoTest
import maryk.datastore.test.DataStoreProcessUpdateTest
import maryk.datastore.test.DurableReplicationTombstoneTest
import maryk.datastore.test.DataStoreScanChangesTest
import maryk.datastore.test.DataStoreScanMultiTypeTest
import maryk.datastore.test.DataStoreScanOnAnyValueIndexTest
import maryk.datastore.test.DataStoreScanOnIndexTest
import maryk.datastore.test.DataStoreScanOnIndexWithPersonTest
import maryk.datastore.test.DataStoreScanOnNormalizeIndexTest
import maryk.datastore.test.DataStoreScanTest
import maryk.datastore.test.DataStoreScanUpdateHistoryTest
import maryk.datastore.test.DataStoreScanUniqueTest
import maryk.datastore.test.DataStoreScanUpdatesAndFlowTest
import maryk.datastore.test.DataStoreScanWithFilterTest
import maryk.datastore.test.DataStoreScanWithMutableValueIndexTest
import maryk.datastore.test.IsDataStoreTest
import maryk.datastore.test.UniqueTest
import maryk.datastore.test.UniqueModel
import maryk.datastore.test.UniqueOwnershipTest
import maryk.datastore.test.assertStatusIs
import maryk.datastore.test.dataModelsForTests
import maryk.lib.extensions.compare.compareTo
import maryk.test.models.CompleteMarykModel
import maryk.test.models.AnyValueSetIndexModel
import maryk.test.models.MarykEnumEmbedded.E1
import maryk.test.models.MarykTypeEnum.T2
import maryk.test.models.ModelV1
import maryk.test.models.ModelV1_1
import maryk.test.models.ModelV2
import maryk.test.models.ModelV2ExtraIndex
import maryk.test.models.Person
import maryk.test.models.SimpleMarykModel
import maryk.test.models.SimpleMarykTypeEnum.S1
import maryk.test.models.TestMarykModel
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

class IndexedDbDataStoreTest {
    @Test
    fun replicationTombstoneSurvivesReopen() = runTest(timeout = indexedDbLongTestTimeout) {
        installIndexedDbForTests()
        val databaseName = "maryk-indexeddb-durable-replication-tombstones-${Random.nextInt()}"
        var dataStore = IndexedDbDataStore.open(databaseName, dataModelsForTests)
        try {
            val test = DurableReplicationTombstoneTest()
            test.writeHardDelete(dataStore)
            dataStore.close()
            dataStore = IndexedDbDataStore.open(databaseName, dataModelsForTests)
            test.replayStaleAdd(dataStore)
        } finally {
            dataStore.close()
        }
    }

    @Test
    fun replicatedUpdatesRespectHardDeleteTombstonesWithoutUpdateHistory() = runTest(timeout = indexedDbLongTestTimeout) {
        installIndexedDbForTests()
        val dataStore = IndexedDbDataStore.open(
            databaseName = "maryk-indexeddb-replication-tombstones-${Random.nextInt()}",
            dataModelsById = dataModelsForTests,
            keepAllVersions = false,
            keepUpdateHistoryIndex = false,
        )
        try {
            runTestCase(DataStoreProcessUpdateTest(dataStore), "executeProcessUpdatesRespectHardDeleteTombstone")
        } finally {
            dataStore.close()
        }
    }

    @Test
    fun uniqueOwnershipHonorsFinalSoftDeletes() = runTest(timeout = indexedDbLongTestTimeout) {
        installIndexedDbForTests()
        val dataStore = IndexedDbDataStore.open(
            databaseName = "maryk-indexeddb-unique-soft-delete-${Random.nextInt()}",
            dataModelsById = mapOf(6u to UniqueModel),
            keepAllVersions = true,
        )

        try {
            UniqueOwnershipTest(dataStore).finalSoftDeleteWithCollidingUniqueReleasesOwnership()
            UniqueOwnershipTest(dataStore).deletedUniqueMutationDoesNotClaimOwnership()
        } finally {
            dataStore.close()
        }
    }

    @Test
    fun scanUpdatesReplaysHardDeleteFromUpdateHistory() = runTest(timeout = indexedDbLongTestTimeout) {
        installIndexedDbForTests()

        val source = IndexedDbDataStore.open(
            databaseName = "maryk-indexeddb-scan-updates-hard-delete-source-${Random.nextInt()}",
            dataModelsById = mapOf(1u to SimpleMarykModel),
            keepUpdateHistoryIndex = true,
        )
        val target = IndexedDbDataStore.open(
            databaseName = "maryk-indexeddb-scan-updates-hard-delete-target-${Random.nextInt()}",
            dataModelsById = mapOf(1u to SimpleMarykModel),
        )

        try {
            val values = SimpleMarykModel.create { value with "hard-delete" }
            val add = assertStatusIs<AddSuccess<SimpleMarykModel>>(
                source.execute(SimpleMarykModel.add(values)).statuses.single()
            )
            target.processUpdate(
                UpdateResponse(
                    SimpleMarykModel,
                    AdditionUpdate(
                        key = add.key,
                        version = add.version,
                        firstVersion = add.version,
                        insertionIndex = 0,
                        isDeleted = false,
                        values = values,
                    )
                )
            )
            val delete = assertStatusIs<DeleteSuccess<SimpleMarykModel>>(
                source.execute(SimpleMarykModel.delete(add.key, hardDelete = true)).statuses.single()
            )
            val hardDeleteMarker = source.byteStore.scan("uh:1")
                .single { (_, value) -> value.contentEquals(byteArrayOf(1)) }
                .second
            assertTrue(hardDeleteMarker.contentEquals(byteArrayOf(1)))

            val updates = source.execute(
                SimpleMarykModel.scanUpdates(
                    fromVersion = add.version,
                    limit = 1u,
                )
            )

            assertIs<OrderedKeysUpdate<SimpleMarykModel>>(updates.updates[0]).apply {
                assertEquals(emptyList(), keys)
                assertEquals(delete.version, version)
            }
            val removal = assertIs<RemovalUpdate<SimpleMarykModel>>(updates.updates[1])
            assertEquals(add.key, removal.key)
            assertEquals(delete.version, removal.version)
            assertEquals(HardDelete, removal.reason)

            target.processUpdate(UpdateResponse(SimpleMarykModel, removal))
            assertTrue(target.execute(SimpleMarykModel.get(add.key)).values.isEmpty())
        } finally {
            source.close()
            target.close()
        }
    }

    @Test
    fun scanUpdatesReturnsHardDeleteWithinVersionBounds() = runTest(timeout = indexedDbLongTestTimeout) {
        installIndexedDbForTests()

        val dataStore = IndexedDbDataStore.open(
            databaseName = "maryk-indexeddb-scan-updates-hard-delete-bounds-${Random.nextInt()}",
            dataModelsById = mapOf(1u to SimpleMarykModel),
            keepAllVersions = true,
            keepUpdateHistoryIndex = true,
        )

        try {
            val add = assertStatusIs<AddSuccess<SimpleMarykModel>>(
                dataStore.execute(SimpleMarykModel.add(SimpleMarykModel.create { value with "ha bounded hard-delete" })).statuses.single()
            )
            val delete = assertStatusIs<DeleteSuccess<SimpleMarykModel>>(
                dataStore.execute(SimpleMarykModel.delete(add.key, hardDelete = true)).statuses.single()
            )

            val updates = dataStore.execute(
                SimpleMarykModel.scanUpdates(
                    fromVersion = delete.version,
                    toVersion = delete.version,
                    limit = 1u,
                )
            )

            assertIs<OrderedKeysUpdate<SimpleMarykModel>>(updates.updates[0]).apply {
                assertEquals(emptyList(), keys)
                assertEquals(delete.version, version)
            }
            assertIs<RemovalUpdate<SimpleMarykModel>>(updates.updates[1]).apply {
                assertEquals(add.key, key)
                assertEquals(delete.version, version)
                assertEquals(HardDelete, reason)
            }
        } finally {
            dataStore.close()
        }
    }

    @Test
    fun scanUpdatesReturnsHardDeleteWithMultipleVersionsRequested() = runTest(timeout = indexedDbLongTestTimeout) {
        installIndexedDbForTests()

        val dataStore = IndexedDbDataStore.open(
            databaseName = "maryk-indexeddb-scan-updates-hard-delete-multi-version-${Random.nextInt()}",
            dataModelsById = mapOf(1u to SimpleMarykModel),
            keepAllVersions = true,
            keepUpdateHistoryIndex = true,
        )

        try {
            val add = assertStatusIs<AddSuccess<SimpleMarykModel>>(
                dataStore.execute(SimpleMarykModel.add(SimpleMarykModel.create { value with "ha multi-version hard-delete" })).statuses.single()
            )
            val delete = assertStatusIs<DeleteSuccess<SimpleMarykModel>>(
                dataStore.execute(SimpleMarykModel.delete(add.key, hardDelete = true)).statuses.single()
            )

            val updates = dataStore.execute(
                SimpleMarykModel.scanUpdates(
                    fromVersion = add.version,
                    toVersion = delete.version,
                    maxVersions = 2u,
                    limit = 1u,
                )
            )

            assertIs<OrderedKeysUpdate<SimpleMarykModel>>(updates.updates[0]).apply {
                assertEquals(emptyList(), keys)
                assertEquals(delete.version, version)
            }
            assertIs<RemovalUpdate<SimpleMarykModel>>(updates.updates[1]).apply {
                assertEquals(add.key, key)
                assertEquals(delete.version, version)
                assertEquals(HardDelete, reason)
            }
        } finally {
            dataStore.close()
        }
    }

    @Test
    fun scanUpdatesReturnsHardDeleteForTrackedOrderedKey() = runTest(timeout = indexedDbLongTestTimeout) {
        installIndexedDbForTests()

        val dataStore = IndexedDbDataStore.open(
            databaseName = "maryk-indexeddb-scan-updates-hard-delete-ordered-${Random.nextInt()}",
            dataModelsById = mapOf(1u to SimpleMarykModel),
            keepAllVersions = true,
            keepUpdateHistoryIndex = true,
        )

        try {
            val add = assertStatusIs<AddSuccess<SimpleMarykModel>>(
                dataStore.execute(SimpleMarykModel.add(SimpleMarykModel.create { value with "ha ordered hard-delete" })).statuses.single()
            )
            val delete = assertStatusIs<DeleteSuccess<SimpleMarykModel>>(
                dataStore.execute(SimpleMarykModel.delete(add.key, hardDelete = true)).statuses.single()
            )

            val updates = dataStore.execute(
                SimpleMarykModel.scanUpdates(
                    fromVersion = add.version,
                    toVersion = delete.version,
                    maxVersions = 2u,
                    orderedKeys = listOf(add.key),
                )
            )

            assertIs<OrderedKeysUpdate<SimpleMarykModel>>(updates.updates[0]).apply {
                assertEquals(emptyList(), keys)
                assertEquals(delete.version, version)
            }
            assertIs<RemovalUpdate<SimpleMarykModel>>(updates.updates[1]).apply {
                assertEquals(add.key, key)
                assertEquals(delete.version, version)
                assertEquals(HardDelete, reason)
            }
        } finally {
            dataStore.close()
        }
    }

    @Test
    fun scanUpdatesReplaysHardDeleteBeforeKeyReuse() = runTest(timeout = indexedDbLongTestTimeout) {
        installIndexedDbForTests()

        val source = IndexedDbDataStore.open(
            databaseName = "maryk-indexeddb-scan-updates-hard-delete-reuse-source-${Random.nextInt()}",
            dataModelsById = mapOf(1u to SimpleMarykModel),
            keepAllVersions = true,
            keepUpdateHistoryIndex = true,
        )
        val target = IndexedDbDataStore.open(
            databaseName = "maryk-indexeddb-scan-updates-hard-delete-reuse-target-${Random.nextInt()}",
            dataModelsById = mapOf(1u to SimpleMarykModel),
            keepAllVersions = true,
        )
        val historicalTarget = IndexedDbDataStore.open(
            databaseName = "maryk-indexeddb-scan-updates-hard-delete-reuse-historical-target-${Random.nextInt()}",
            dataModelsById = mapOf(1u to SimpleMarykModel),
            keepAllVersions = true,
        )

        try {
            val originalValues = SimpleMarykModel.create { value with "ha original" }
            val original = assertStatusIs<AddSuccess<SimpleMarykModel>>(
                source.execute(SimpleMarykModel.add(originalValues)).statuses.single()
            )
            val originalUpdate = AdditionUpdate(
                key = original.key,
                version = original.version,
                firstVersion = original.version,
                insertionIndex = 0,
                isDeleted = false,
                values = originalValues,
            )
            target.processUpdate(UpdateResponse(SimpleMarykModel, originalUpdate))
            historicalTarget.processUpdate(UpdateResponse(SimpleMarykModel, originalUpdate))
            val delete = assertStatusIs<DeleteSuccess<SimpleMarykModel>>(
                source.execute(SimpleMarykModel.delete(original.key, hardDelete = true)).statuses.single()
            )
            val replacementValues = SimpleMarykModel.create { value with "ha replacement" }
            val replacement = assertStatusIs<AddSuccess<SimpleMarykModel>>(
                source.execute(SimpleMarykModel.add(original.key to replacementValues)).statuses.single()
            )

            val updates = source.execute(
                SimpleMarykModel.scanUpdates(
                    fromVersion = delete.version,
                    limit = 1u,
                    maxVersions = 1u,
                )
            )

            assertIs<OrderedKeysUpdate<SimpleMarykModel>>(updates.updates[0]).apply {
                assertEquals(listOf(original.key), keys)
                assertEquals(replacement.version, version)
            }
            assertIs<RemovalUpdate<SimpleMarykModel>>(updates.updates[1]).apply {
                assertEquals(original.key, key)
                assertEquals(delete.version, version)
                assertEquals(HardDelete, reason)
            }
            assertIs<AdditionUpdate<SimpleMarykModel>>(updates.updates[2]).apply {
                assertEquals(original.key, key)
                assertEquals(replacement.version, version)
                assertEquals(replacementValues, values)
            }
            updates.updates.drop(1).forEach { target.processUpdate(UpdateResponse(SimpleMarykModel, it)) }
            assertEquals(replacementValues, target.execute(SimpleMarykModel.get(original.key)).values.single().values)

            val historicalUpdates = source.execute(
                SimpleMarykModel.scanUpdates(
                    fromVersion = delete.version,
                    toVersion = delete.version,
                    limit = 1u,
                    maxVersions = 2u,
                )
            )
            assertIs<OrderedKeysUpdate<SimpleMarykModel>>(historicalUpdates.updates[0]).apply {
                assertEquals(emptyList(), keys)
                assertEquals(delete.version, version)
            }
            assertIs<RemovalUpdate<SimpleMarykModel>>(historicalUpdates.updates[1]).apply {
                assertEquals(original.key, key)
                assertEquals(delete.version, version)
                assertEquals(HardDelete, reason)
            }
            historicalTarget.processUpdate(UpdateResponse(SimpleMarykModel, historicalUpdates.updates[1]))
            assertTrue(historicalTarget.execute(SimpleMarykModel.get(original.key)).values.isEmpty())

            val secondDelete = assertStatusIs<DeleteSuccess<SimpleMarykModel>>(
                source.execute(SimpleMarykModel.delete(original.key, hardDelete = true)).statuses.single()
            )
            val deleteOnlyUpdates = source.execute(
                SimpleMarykModel.scanUpdates(
                    fromVersion = delete.version,
                    limit = 1u,
                    maxVersions = 1u,
                )
            )
            assertEquals(2, deleteOnlyUpdates.updates.size)
            assertIs<RemovalUpdate<SimpleMarykModel>>(deleteOnlyUpdates.updates[1]).apply {
                assertEquals(original.key, key)
                assertEquals(secondDelete.version, version)
                assertEquals(HardDelete, reason)
            }
            target.processUpdate(UpdateResponse(SimpleMarykModel, deleteOnlyUpdates.updates[1]))
            assertTrue(target.execute(SimpleMarykModel.get(original.key)).values.isEmpty())

            val finalValues = SimpleMarykModel.create { value with "ha final replacement" }
            val finalAdd = assertStatusIs<AddSuccess<SimpleMarykModel>>(
                source.execute(SimpleMarykModel.add(original.key to finalValues)).statuses.single()
            )
            val latestCycleUpdates = source.execute(
                SimpleMarykModel.scanUpdates(
                    fromVersion = delete.version,
                    limit = 1u,
                    maxVersions = 2u,
                )
            )
            assertEquals(3, latestCycleUpdates.updates.size)
            assertIs<RemovalUpdate<SimpleMarykModel>>(latestCycleUpdates.updates[1]).apply {
                assertEquals(secondDelete.version, version)
            }
            assertIs<AdditionUpdate<SimpleMarykModel>>(latestCycleUpdates.updates[2]).apply {
                assertEquals(finalAdd.version, version)
                assertEquals(finalValues, values)
            }
            latestCycleUpdates.updates.drop(1).forEach { target.processUpdate(UpdateResponse(SimpleMarykModel, it)) }
            assertEquals(finalValues, target.execute(SimpleMarykModel.get(original.key)).values.single().values)

            val historicalCycleUpdates = source.execute(
                SimpleMarykModel.scanUpdates(
                    fromVersion = delete.version,
                    toVersion = delete.version,
                    limit = 1u,
                    maxVersions = 2u,
                )
            )
            assertIs<RemovalUpdate<SimpleMarykModel>>(historicalCycleUpdates.updates[1]).apply {
                assertEquals(delete.version, version)
            }
            val liveCycleUpdates = source.execute(
                SimpleMarykModel.scanUpdates(
                    fromVersion = delete.version,
                    limit = 1u,
                )
            )
            assertIs<RemovalUpdate<SimpleMarykModel>>(liveCycleUpdates.updates[1]).apply {
                assertEquals(secondDelete.version, version)
            }
        } finally {
            source.close()
            target.close()
            historicalTarget.close()
        }
    }

    @Test
    fun scanUpdatesLimitsHardDeletesWithCurrentCandidates() = runTest(timeout = indexedDbLongTestTimeout) {
        installIndexedDbForTests()

        val dataStore = IndexedDbDataStore.open(
            databaseName = "maryk-indexeddb-scan-updates-hard-delete-limit-${Random.nextInt()}",
            dataModelsById = mapOf(1u to SimpleMarykModel),
            keepAllVersions = true,
            keepUpdateHistoryIndex = true,
        )

        try {
            val first = assertStatusIs<AddSuccess<SimpleMarykModel>>(
                dataStore.execute(SimpleMarykModel.add(SimpleMarykModel.create { value with "ha first hard-delete" })).statuses.single()
            )
            val second = assertStatusIs<AddSuccess<SimpleMarykModel>>(
                dataStore.execute(SimpleMarykModel.add(SimpleMarykModel.create { value with "ha second hard-delete" })).statuses.single()
            )
            val firstDelete = assertStatusIs<DeleteSuccess<SimpleMarykModel>>(
                dataStore.execute(SimpleMarykModel.delete(first.key, hardDelete = true)).statuses.single()
            )
            val secondDelete = assertStatusIs<DeleteSuccess<SimpleMarykModel>>(
                dataStore.execute(SimpleMarykModel.delete(second.key, hardDelete = true)).statuses.single()
            )

            val updates = dataStore.execute(
                SimpleMarykModel.scanUpdates(
                    fromVersion = firstDelete.version,
                    limit = 1u,
                    maxVersions = 2u,
                )
            )

            val expected = listOf(first to firstDelete, second to secondDelete)
                .minWith { firstCandidate, secondCandidate ->
                    firstCandidate.first.key.bytes.compareTo(secondCandidate.first.key.bytes)
                }

            assertIs<OrderedKeysUpdate<SimpleMarykModel>>(updates.updates[0]).apply {
                assertEquals(emptyList(), keys)
                assertEquals(expected.second.version, version)
            }
            assertEquals(2, updates.updates.size)
            assertIs<RemovalUpdate<SimpleMarykModel>>(updates.updates[1]).apply {
                assertEquals(expected.first.key, key)
                assertEquals(expected.second.version, version)
                assertEquals(HardDelete, reason)
            }
        } finally {
            dataStore.close()
        }
    }

    @Test
    fun scanUpdatesPaginatesHistoricHardDeletesInDescendingKeyOrder() = runTest(timeout = indexedDbLongTestTimeout) {
        installIndexedDbForTests()

        val databaseName = "maryk-indexeddb-scan-updates-hard-delete-history-pagination-${Random.nextInt()}"
        val keyA = Key<SimpleMarykModel>(ByteArray(SimpleMarykModel.Meta.keyByteSize).also { it[it.lastIndex] = 1 })
        val keyB = Key<SimpleMarykModel>(ByteArray(SimpleMarykModel.Meta.keyByteSize).also { it[it.lastIndex] = 2 })
        val deleteVersionA = 17uL
        val deleteVersionB = 31uL
        val history = openIndexedDbByteStore(databaseName, setOf("meta", "uh:1", "hd:1", "hdk:1"))
        history.writeBatch(
            listOf(
                IndexedDbWriteOperation.Put("hdk:1", createHardDeleteHistoryRowKey(keyA.bytes, deleteVersionA), byteArrayOf(1)),
                IndexedDbWriteOperation.Put("hdk:1", createHardDeleteHistoryRowKey(keyB.bytes, deleteVersionB), byteArrayOf(1)),
            )
        )
        history.close()

        val dataStore = IndexedDbDataStore.open(
            databaseName = databaseName,
            dataModelsById = mapOf(1u to SimpleMarykModel),
            keepAllVersions = true,
            keepUpdateHistoryIndex = true,
        )
        try {
            val firstPage = dataStore.execute(
                SimpleMarykModel.scanUpdates(
                    fromVersion = 1uL,
                    toVersion = deleteVersionB,
                    startKey = keyB,
                    order = descendingOrder,
                    limit = 1u,
                )
            )
            assertIs<RemovalUpdate<SimpleMarykModel>>(firstPage.updates[1]).apply {
                assertEquals(keyB, key)
                assertEquals(deleteVersionB, version)
                assertEquals(HardDelete, reason)
            }

            val secondPage = dataStore.execute(
                SimpleMarykModel.scanUpdates(
                    fromVersion = 1uL,
                    toVersion = deleteVersionB,
                    startKey = keyB,
                    includeStart = false,
                    order = descendingOrder,
                    limit = 1u,
                )
            )
            assertIs<RemovalUpdate<SimpleMarykModel>>(secondPage.updates[1]).apply {
                assertEquals(keyA, key)
                assertEquals(deleteVersionA, version)
                assertEquals(HardDelete, reason)
            }
        } finally {
            dataStore.close()
        }
    }

    @Test
    fun scanUpdatesHistoricHardDeleteLimitDoesNotReadEveryVersionForOneKey() = runTest(timeout = indexedDbLongTestTimeout) {
        installIndexedDbForTests()

        val databaseName = "maryk-indexeddb-scan-updates-hard-delete-history-bounded-${Random.nextInt()}"
        val key = Key<SimpleMarykModel>(ByteArray(SimpleMarykModel.Meta.keyByteSize) { 5 })
        val history = openIndexedDbByteStore(databaseName, setOf("meta", "uh:1", "hd:1", "hdk:1"))
        val latestVersion = 512uL
        history.writeBatch(
            (1uL..latestVersion).map { version ->
                IndexedDbWriteOperation.Put("hdk:1", createHardDeleteHistoryRowKey(key.bytes, version), byteArrayOf(1))
            }
        )
        history.close()

        var hdkRowsRead = 0
        hardDeleteHistoryRowReadObserver = { hdkRowsRead++ }
        val dataStore = IndexedDbDataStore.open(
            databaseName = databaseName,
            dataModelsById = mapOf(1u to SimpleMarykModel),
            keepAllVersions = true,
            keepUpdateHistoryIndex = true,
        )
        try {
            val updates = dataStore.execute(
                SimpleMarykModel.scanUpdates(
                    fromVersion = 1uL,
                    toVersion = latestVersion,
                    limit = 1u,
                )
            )
            assertIs<RemovalUpdate<SimpleMarykModel>>(updates.updates[1]).apply {
                assertEquals(key, this.key)
                assertEquals(latestVersion, version)
            }
            assertTrue(hdkRowsRead <= 2, "Expected bounded hdk reads, got $hdkRowsRead")
        } finally {
            hardDeleteHistoryRowReadObserver = null
            dataStore.close()
        }
    }

    @Test
    fun scanUpdatesDoesNotEvaluateHardDeletesAgainstWhere() = runTest(timeout = indexedDbLongTestTimeout) {
        installIndexedDbForTests()

        val dataStore = IndexedDbDataStore.open(
            databaseName = "maryk-indexeddb-scan-updates-hard-delete-where-${Random.nextInt()}",
            dataModelsById = mapOf(1u to SimpleMarykModel),
            keepUpdateHistoryIndex = true,
        )

        try {
            val add = assertStatusIs<AddSuccess<SimpleMarykModel>>(
                dataStore.execute(SimpleMarykModel.add(SimpleMarykModel.create { value with "ha filtered hard-delete" })).statuses.single()
            )
            val delete = assertStatusIs<DeleteSuccess<SimpleMarykModel>>(
                dataStore.execute(SimpleMarykModel.delete(add.key, hardDelete = true)).statuses.single()
            )

            val updates = dataStore.execute(
                SimpleMarykModel.scanUpdates(
                    fromVersion = delete.version,
                    where = Equals(SimpleMarykModel { value::ref } with "ha filtered hard-delete"),
                    limit = 1u,
                )
            )

            assertIs<OrderedKeysUpdate<SimpleMarykModel>>(updates.updates.single()).apply {
                assertEquals(emptyList(), keys)
                assertEquals(0uL, version)
            }
        } finally {
            dataStore.close()
        }
    }

    @Test
    fun scanUpdatesTracksHardDeleteVersionForFilteredAndOrderedKeys() = runTest(timeout = indexedDbLongTestTimeout) {
        installIndexedDbForTests()

        val dataStore = IndexedDbDataStore.open(
            databaseName = "maryk-indexeddb-scan-updates-hard-delete-tracked-${Random.nextInt()}",
            dataModelsById = mapOf(1u to SimpleMarykModel),
            keepUpdateHistoryIndex = true,
        )

        try {
            val add = assertStatusIs<AddSuccess<SimpleMarykModel>>(
                dataStore.execute(SimpleMarykModel.add(SimpleMarykModel.create { value with "ha tracked hard-delete" })).statuses.single()
            )
            val delete = assertStatusIs<DeleteSuccess<SimpleMarykModel>>(
                dataStore.execute(SimpleMarykModel.delete(add.key, hardDelete = true)).statuses.single()
            )

            val filteredUpdates = dataStore.execute(
                SimpleMarykModel.scanUpdates(
                    fromVersion = delete.version,
                    where = Equals(SimpleMarykModel { value::ref } with "ha tracked hard-delete"),
                    orderedKeys = listOf(add.key),
                    limit = 1u,
                )
            )
            assertIs<RemovalUpdate<SimpleMarykModel>>(filteredUpdates.updates[1]).apply {
                assertEquals(add.key, key)
                assertEquals(delete.version, version)
                assertEquals(HardDelete, reason)
            }

            val indexedDataStore = IndexedDbDataStore.open(
                databaseName = "maryk-indexeddb-scan-updates-hard-delete-indexed-${Random.nextInt()}",
                dataModelsById = mapOf(1u to AnyValueSetIndexModel),
                keepUpdateHistoryIndex = true,
            )
            try {
                val indexedAdd = assertStatusIs<AddSuccess<AnyValueSetIndexModel>>(
                    indexedDataStore.execute(
                        AnyValueSetIndexModel.add(
                            AnyValueSetIndexModel.create {
                                name with "ha indexed hard-delete"
                                setValues with setOf("hard-delete")
                            }
                        )
                    ).statuses.single()
                )
                val indexedDelete = assertStatusIs<DeleteSuccess<AnyValueSetIndexModel>>(
                    indexedDataStore.execute(AnyValueSetIndexModel.delete(indexedAdd.key, hardDelete = true)).statuses.single()
                )
                val orderedUpdates = indexedDataStore.execute(
                    AnyValueSetIndexModel.scanUpdates(
                        fromVersion = indexedDelete.version,
                        order = AnyValueSetIndexModel { setValues.refToAny() }.ascending(),
                        orderedKeys = listOf(indexedAdd.key),
                        limit = 1u,
                    )
                )
                assertIs<RemovalUpdate<AnyValueSetIndexModel>>(orderedUpdates.updates[1]).apply {
                    assertEquals(indexedAdd.key, key)
                    assertEquals(indexedDelete.version, version)
                    assertEquals(HardDelete, reason)
                }
            } finally {
                indexedDataStore.close()
            }
        } finally {
            dataStore.close()
        }
    }

    @Test
    fun scanUpdatesBackfillsLegacyHardDeleteTombstoneAfterSchemaUpgrade() = runTest(timeout = indexedDbLongTestTimeout) {
        installIndexedDbForTests()

        val databaseName = "maryk-indexeddb-scan-updates-hard-delete-legacy-${Random.nextInt()}"
        val deleteVersion = 42uL
        val legacyKey = Key<SimpleMarykModel>(ByteArray(SimpleMarykModel.Meta.keyByteSize) { 1 })
        val legacyHistory = openIndexedDbByteStore(databaseName, setOf("meta", "uh:1"))
        legacyHistory.put("uh:1", createUpdateHistoryRowKey(deleteVersion, legacyKey.bytes), byteArrayOf(1))
        legacyHistory.close()

        val upgradedDataStore = IndexedDbDataStore.open(
            databaseName = databaseName,
            dataModelsById = mapOf(1u to SimpleMarykModel),
            keepUpdateHistoryIndex = true,
        )
        try {
            val updates = upgradedDataStore.execute(
                SimpleMarykModel.scanUpdates(
                    fromVersion = deleteVersion,
                    limit = 1u,
                )
            )
            assertIs<RemovalUpdate<SimpleMarykModel>>(updates.updates[1]).apply {
                assertEquals(legacyKey, key)
                assertEquals(deleteVersion, version)
                assertEquals(HardDelete, reason)
            }
            assertTrue(upgradedDataStore.byteStore.get("hd:1", legacyKey.bytes)?.contentEquals(deleteVersion.toBigEndianBytes()) == true)
            assertTrue(upgradedDataStore.byteStore.get("meta", "hard-delete-history:1".encodeToByteArray()) != null)
        } finally {
            upgradedDataStore.close()
        }
    }

    @Test
    fun hardDeleteTombstoneMigrationRefreshesNewerLegacyVersionAndIsIdempotentOnReopen() = runTest(timeout = indexedDbLongTestTimeout) {
        installIndexedDbForTests()

        val databaseName = "maryk-indexeddb-hard-delete-migration-version-${Random.nextInt()}"
        val key = Key<SimpleMarykModel>(ByteArray(SimpleMarykModel.Meta.keyByteSize) { 3 })
        val existingVersion = 9uL
        val legacyVersion = 13uL
        val legacyHistory = openIndexedDbByteStore(databaseName, setOf("meta", "uh:1", "hd:1", "hdk:1"))
        legacyHistory.writeBatch(
            listOf(
                IndexedDbWriteOperation.Put("uh:1", createUpdateHistoryRowKey(legacyVersion, key.bytes), byteArrayOf(1)),
                IndexedDbWriteOperation.Put("hd:1", key.bytes, existingVersion.toBigEndianBytes()),
            )
        )
        legacyHistory.close()

        val dataStore = IndexedDbDataStore.open(
            databaseName = databaseName,
            dataModelsById = mapOf(1u to SimpleMarykModel),
            keepUpdateHistoryIndex = true,
        )
        try {
            assertTrue(dataStore.byteStore.get("hd:1", key.bytes)?.contentEquals(legacyVersion.toBigEndianBytes()) == true)
            val firstHistoryRows = dataStore.byteStore.scan("hdk:1").map { it.first }
            assertEquals(1, firstHistoryRows.size)
            assertTrue(firstHistoryRows.single().contentEquals(createHardDeleteHistoryRowKey(key.bytes, legacyVersion)))
            assertTrue(dataStore.byteStore.get("meta", "hard-delete-history:1".encodeToByteArray()) != null)
        } finally {
            dataStore.close()
        }

        val reopened = IndexedDbDataStore.open(
            databaseName = databaseName,
            dataModelsById = mapOf(1u to SimpleMarykModel),
            keepUpdateHistoryIndex = true,
        )
        try {
            assertTrue(reopened.byteStore.get("hd:1", key.bytes)?.contentEquals(legacyVersion.toBigEndianBytes()) == true)
            val reopenedHistoryRows = reopened.byteStore.scan("hdk:1").map { it.first }
            assertEquals(1, reopenedHistoryRows.size)
            assertTrue(reopenedHistoryRows.single().contentEquals(createHardDeleteHistoryRowKey(key.bytes, legacyVersion)))
            assertTrue(reopened.byteStore.get("meta", "hard-delete-history:1".encodeToByteArray()) != null)
        } finally {
            reopened.close()
        }
    }

    @Test
    fun hardDeleteTombstoneMigrationDoesNotRegressNativeDeleteWrittenAfterLegacyScan() = runTest(timeout = indexedDbLongTestTimeout) {
        installIndexedDbForTests()

        val databaseName = "maryk-indexeddb-hard-delete-migration-race-${Random.nextInt()}"
        val key = Key<SimpleMarykModel>(ByteArray(SimpleMarykModel.Meta.keyByteSize) { 4 })
        val legacyVersion = 2uL
        val nativeVersion = 4uL
        val legacyHistory = openIndexedDbByteStore(databaseName, setOf("meta", "uh:1", "hd:1", "hdk:1"))
        legacyHistory.put("uh:1", createUpdateHistoryRowKey(legacyVersion, key.bytes), byteArrayOf(1))
        legacyHistory.close()

        val migrationReachedWrite = CompletableDeferred<Unit>()
        val continueMigration = CompletableDeferred<Unit>()
        hardDeleteTombstoneMigrationBeforeWrite = { _, _, _ ->
            migrationReachedWrite.complete(Unit)
            continueMigration.await()
        }
        var migratedDataStore: IndexedDbDataStore? = null
        try {
            val migration = async {
                IndexedDbDataStore.open(
                    databaseName = databaseName,
                    dataModelsById = mapOf(1u to SimpleMarykModel),
                    keepUpdateHistoryIndex = true,
                )
            }
            migrationReachedWrite.await()

            val nativeWrite = async {
                val nativeWriter = openIndexedDbByteStore(databaseName, setOf("meta", "uh:1", "hd:1", "hdk:1"))
                try {
                    nativeWriter.transaction(setOf("hd:1", "hdk:1"), IndexedDbTransactionMode.READWRITE) { store ->
                        store.writeBatch(
                            listOf(
                                IndexedDbWriteOperation.Put("hd:1", key.bytes, nativeVersion.toBigEndianBytes()),
                                IndexedDbWriteOperation.Put("hdk:1", createHardDeleteHistoryRowKey(key.bytes, nativeVersion), byteArrayOf(1)),
                            )
                        )
                    }
                } finally {
                    nativeWriter.close()
                }
            }
            var nativeWriteWaits = 0
            while (!nativeWrite.isCompleted && nativeWriteWaits++ < 1_000) {
                delay(1)
            }
            assertFalse(nativeWrite.isCompleted)

            continueMigration.complete(Unit)
            migratedDataStore = migration.await()
            nativeWrite.await()
            assertTrue(migratedDataStore.byteStore.get("hd:1", key.bytes)?.contentEquals(nativeVersion.toBigEndianBytes()) == true)
            val historyRows = migratedDataStore.byteStore.scan("hdk:1").map { it.first }
            assertEquals(2, historyRows.size)
            assertTrue(historyRows[0].contentEquals(createHardDeleteHistoryRowKey(key.bytes, nativeVersion)))
            assertTrue(historyRows[1].contentEquals(createHardDeleteHistoryRowKey(key.bytes, legacyVersion)))
            assertTrue(migratedDataStore.byteStore.get("meta", "hard-delete-history:1".encodeToByteArray()) != null)
        } finally {
            hardDeleteTombstoneMigrationBeforeWrite = null
            if (!continueMigration.isCompleted) continueMigration.complete(Unit)
            migratedDataStore?.close()
        }
    }

    @Test
    fun replicationTombstoneCompactionDoesNotDeleteNewerConcurrentVersion() = runTest(timeout = indexedDbLongTestTimeout) {
        installIndexedDbForTests()

        val dataStore = IndexedDbDataStore.open(
            databaseName = "maryk-indexeddb-tombstone-compaction-race-${Random.nextInt()}",
            dataModelsById = mapOf(1u to SimpleMarykModel),
        )
        val key = Key<SimpleMarykModel>(ByteArray(SimpleMarykModel.Meta.keyByteSize) { 5 })
        try {
            dataStore.byteStore.put("hd:1", key.bytes, 10uL.toBigEndianBytes())
            replicationTombstoneCompactionAfterScan = { modelId ->
                dataStore.byteStore.put("hd:$modelId", key.bytes, 20uL.toBigEndianBytes())
                replicationTombstoneCompactionAfterScan = null
            }

            dataStore.compactReplicationTombstones(10uL)

            assertTrue(dataStore.byteStore.get("hd:1", key.bytes)?.contentEquals(20uL.toBigEndianBytes()) == true)
        } finally {
            replicationTombstoneCompactionAfterScan = null
            dataStore.close()
        }
    }

    @Test
    fun getUpdatesReplaysHistoricChangesInsteadOfCurrentState() = runTest(timeout = indexedDbLongTestTimeout) {
        installIndexedDbForTests()

        val source = IndexedDbDataStore.open(
            databaseName = "maryk-indexeddb-get-updates-history-source-${Random.nextInt()}",
            dataModelsById = mapOf(1u to SimpleMarykModel),
            keepAllVersions = true,
        )
        val target = IndexedDbDataStore.open(
            databaseName = "maryk-indexeddb-get-updates-history-target-${Random.nextInt()}",
            dataModelsById = mapOf(1u to SimpleMarykModel),
            keepAllVersions = true,
        )

        try {
            val initialValues = SimpleMarykModel.create { value with "haha-initial" }
            val add = source.execute(SimpleMarykModel.add(initialValues))
            val addStatus = assertStatusIs<AddSuccess<SimpleMarykModel>>(add.statuses.single())
            val changedValue = "haha-changed"
            val change = Change(SimpleMarykModel { value::ref } with changedValue)
            val changeResponse = source.execute(SimpleMarykModel.change(addStatus.key.change(change)))
            val changeStatus = assertStatusIs<ChangeSuccess<SimpleMarykModel>>(changeResponse.statuses.single())
            val deleteResponse = source.execute(SimpleMarykModel.delete(addStatus.key))
            val deleteStatus = assertStatusIs<DeleteSuccess<SimpleMarykModel>>(deleteResponse.statuses.single())
            val missingKey = Key<SimpleMarykModel>(
                ByteArray(SimpleMarykModel.Meta.keyByteSize) { 0xFF.toByte() }
            )

            val history = source.execute(
                SimpleMarykModel.getUpdates(
                    addStatus.key,
                    missingKey,
                    fromVersion = addStatus.version,
                    toVersion = deleteStatus.version,
                    maxVersions = 10u,
                    filterSoftDeleted = false,
                )
            )

            assertIs<OrderedKeysUpdate<SimpleMarykModel>>(history.updates[0]).apply {
                assertEquals(listOf(addStatus.key), keys)
                assertEquals(deleteStatus.version, version)
            }
            assertIs<AdditionUpdate<SimpleMarykModel>>(history.updates[1]).apply {
                assertEquals(addStatus.version, version)
                assertEquals(addStatus.version, firstVersion)
                assertEquals(initialValues, values)
                assertFalse(isDeleted)
            }
            assertIs<ChangeUpdate<SimpleMarykModel>>(history.updates[2]).apply {
                assertEquals(changeStatus.version, version)
                assertEquals(listOf(change), changes)
            }
            assertIs<ChangeUpdate<SimpleMarykModel>>(history.updates[3]).apply {
                assertEquals(deleteStatus.version, version)
                assertEquals(listOf(ObjectSoftDeleteChange(true)), changes)
            }

            for (update in history.updates.drop(1)) {
                target.processUpdate(UpdateResponse(SimpleMarykModel, update))
            }
            val replayed = target.execute(SimpleMarykModel.get(addStatus.key, filterSoftDeleted = false)).values.single()
            assertEquals(SimpleMarykModel.create { value with changedValue }, replayed.values)
            assertTrue(replayed.isDeleted)
        } finally {
            source.close()
            target.close()
        }
    }

    @Test
    fun getUpdatesReconstructsMarkerOnlyCreationFromUnchangedCurrentState() = runTest {
        installIndexedDbForTests()

        val dataStore = IndexedDbDataStore.open(
            databaseName = "maryk-indexeddb-get-updates-marker-current-${Random.nextInt()}",
            dataModelsById = mapOf(2u to SimpleMarykModel),
        )
        try {
            val values = SimpleMarykModel.create { value with "haha-marker-current" }
            val add = assertIs<AddSuccess<SimpleMarykModel>>(
                dataStore.execute(SimpleMarykModel.add(values)).statuses.single()
            )
            dataStore.byteStore.put(
                "c:2",
                createChangeLogRowKey(add.key.bytes, add.version),
                byteArrayOf(0),
            )

            val updates = dataStore.execute(
                SimpleMarykModel.getUpdates(
                    add.key,
                    fromVersion = add.version,
                )
            )

            assertIs<AdditionUpdate<SimpleMarykModel>>(updates.updates[1]).apply {
                assertEquals(add.key, key)
                assertEquals(add.version, version)
                assertEquals(values, this.values)
            }
        } finally {
            dataStore.close()
        }
    }

    @Test
    fun getUpdatesRejectsMarkerOnlyCreationWhenCurrentStateHasChanged() = runTest {
        installIndexedDbForTests()

        val dataStore = IndexedDbDataStore.open(
            databaseName = "maryk-indexeddb-get-updates-marker-changed-${Random.nextInt()}",
            dataModelsById = mapOf(2u to SimpleMarykModel),
        )
        try {
            val add = assertIs<AddSuccess<SimpleMarykModel>>(
                dataStore.execute(
                    SimpleMarykModel.add(SimpleMarykModel.create { value with "haha-marker-original" })
                ).statuses.single()
            )
            dataStore.byteStore.put(
                "c:2",
                createChangeLogRowKey(add.key.bytes, add.version),
                byteArrayOf(0),
            )
            assertIs<ChangeSuccess<SimpleMarykModel>>(
                dataStore.execute(
                    SimpleMarykModel.change(
                        add.key.change(Change(SimpleMarykModel { value::ref } with "haha-marker-changed"))
                    )
                ).statuses.single()
            )

            assertFailsWith<RequestException> {
                dataStore.execute(
                    SimpleMarykModel.getUpdates(
                        add.key,
                        fromVersion = add.version,
                    )
                )
            }
        } finally {
            dataStore.close()
        }
    }

    @Test
    fun scanUpdatesReconstructsMarkerOnlyCreationFromUnchangedCurrentState() = runTest {
        installIndexedDbForTests()

        val dataStore = IndexedDbDataStore.open(
            databaseName = "maryk-indexeddb-scan-updates-marker-current-${Random.nextInt()}",
            dataModelsById = mapOf(2u to SimpleMarykModel),
        )
        try {
            val values = SimpleMarykModel.create { value with "haha-scan-marker-current" }
            val add = assertIs<AddSuccess<SimpleMarykModel>>(
                dataStore.execute(SimpleMarykModel.add(values)).statuses.single()
            )
            dataStore.byteStore.put(
                "c:2",
                createChangeLogRowKey(add.key.bytes, add.version),
                byteArrayOf(0),
            )

            val updates = dataStore.execute(
                SimpleMarykModel.scanUpdates(fromVersion = add.version)
            )

            assertIs<AdditionUpdate<SimpleMarykModel>>(updates.updates[1]).apply {
                assertEquals(add.key, key)
                assertEquals(add.version, version)
                assertEquals(values, this.values)
            }
        } finally {
            dataStore.close()
        }
    }

    @Test
    fun scanUpdatesRejectsMarkerOnlyCreationWhenCurrentStateHasChanged() = runTest {
        installIndexedDbForTests()

        val dataStore = IndexedDbDataStore.open(
            databaseName = "maryk-indexeddb-scan-updates-marker-changed-${Random.nextInt()}",
            dataModelsById = mapOf(2u to SimpleMarykModel),
        )
        try {
            val add = assertIs<AddSuccess<SimpleMarykModel>>(
                dataStore.execute(
                    SimpleMarykModel.add(SimpleMarykModel.create { value with "haha-scan-marker-original" })
                ).statuses.single()
            )
            dataStore.byteStore.put(
                "c:2",
                createChangeLogRowKey(add.key.bytes, add.version),
                byteArrayOf(0),
            )
            assertIs<ChangeSuccess<SimpleMarykModel>>(
                dataStore.execute(
                    SimpleMarykModel.change(
                        add.key.change(Change(SimpleMarykModel { value::ref } with "haha-scan-marker-changed"))
                    )
                ).statuses.single()
            )

            assertFailsWith<RequestException> {
                dataStore.execute(SimpleMarykModel.scanUpdates(fromVersion = add.version))
            }
        } finally {
            dataStore.close()
        }
    }

    @Test
    fun portableBackupRoundTrip() = runTest(timeout = indexedDbLongTestTimeout) {
        installIndexedDbForTests()

        val models = mapOf(1u to SimpleMarykModel)
        val source = IndexedDbDataStore.open(
            databaseName = "maryk-indexeddb-backup-source-${Random.nextInt()}",
            dataModelsById = models,
            keepAllVersions = true,
        )
        val target = IndexedDbDataStore.open(
            databaseName = "maryk-indexeddb-backup-target-${Random.nextInt()}",
            dataModelsById = models,
            keepAllVersions = true,
        )

        try {
            DataStoreBackupRoundTripTest(source, target, useAutomaticSnapshot = false)
                .restoresCurrentValueAndCompleteHistory()
        } finally {
            source.close()
            target.close()
        }
    }

    @Test
    fun reusesSharedAddGetScanTests() = runTest(timeout = indexedDbLongTestTimeout) {
        installIndexedDbForTests()

        val dataStore = IndexedDbDataStore.open(
            databaseName = "maryk-indexeddb-${Random.nextInt()}",
            dataModelsById = dataModelsForTests,
        )

        try {
            runTestCase(DataStoreAddTest(dataStore), "executeAddAndSimpleGetRequest")
            runTestCase(DataStoreAddTest(dataStore), "executeAddWithKeyAndSimpleGetRequest")
            runTestCase(DataStoreAddTest(dataStore), "notAddSameObjectTwice")
            runTestCase(DataStoreChangeTest(dataStore), "executeChangeCheckRequest")
            runTestCase(DataStoreChangeTest(dataStore), "executeChangeChangeRequest")
            runTestCase(DataStoreChangeTest(dataStore), "executeChangeDeleteRequest")
            runTestCase(DataStoreChangeTest(dataStore), "executeChangeDeleteComplexRequest")
            runTestCase(DataStoreChangeTest(dataStore), "executeChangeDeleteComplexItemsRequest")
            runTestCase(DataStoreChangeTest(dataStore), "executeChangeListRequest")
            runTestCase(DataStoreChangeTest(dataStore), "executeChangeSetRequest")
            runTestCase(DataStoreChangeTest(dataStore), "executeChangeMapRequest")
            runTestCase(DataStoreChangeTest(dataStore), "executeChangeNoOpListAndMapRequest")
            runTestCase(DataStoreChangeTest(dataStore), "executeChangeIncMapRequest")
            runTestCase(DataStoreChangeComplexTest(dataStore), "executeChangeDeleteMapSubValueRequest")
            runTestCase(DataStoreChangeComplexTest(dataStore), "executeChangeDeleteMapTypedSubValueRequest")
            runTestCase(DataStoreChangeComplexTest(dataStore), "executeChangeChangeValueRequest")
            runTestCase(DataStoreChangeComplexTest(dataStore), "executeChangeInsertValueRequest")
            runTestCase(DataStoreChangeValidationTest(dataStore), "executeChangeChangeWithValidationExceptionRequest")
            runTestCase(DataStoreChangeValidationTest(dataStore), "executeChangeListWithTooManyItemsValidationExceptionRequest")
            runTestCase(DataStoreChangeValidationTest(dataStore), "executeChangeSetWithMaxSizeValidationExceptionRequest")
            runTestCase(DataStoreChangeValidationTest(dataStore), "executeChangeMapWithSizeValidationExceptionRequest")
            runTestCase(DataStoreChangeValidationTest(dataStore), "executeChangeListSizeValidationExceptionRequest")
            runTestCase(DataStoreDeleteTest(dataStore), "processHardDeleteRequest")
            runTestCase(DataStoreGetChangesTest(dataStore), "executeSimpleGetChangesRequest")
            runTestCase(DataStoreGetChangesTest(dataStore), "executeToVersionGetChangesRequest")
            runTestCase(DataStoreGetChangesTest(dataStore), "executeFromVersionGetChangesRequest")
            runTestCase(DataStoreGetChangesTest(dataStore), "executeGetChangesRequestWithSelect")
            runTestCase(DataStoreGetChangesTest(dataStore), "executeGetChangesRequestWithMaxVersions")
            runTestCase(DataStoreGetTest(dataStore), "executeSimpleGetRequest")
            runTestCase(DataStoreGetUpdatesAndFlowTest(dataStore), "executeSimpleGetUpdatesRequest")
            runTestCase(DataStoreFilterTest(dataStore), "doComplexMapListSetFilter")
            runTestCase(DataStoreFilterTest(dataStore), "doReferencedEqualsFilter")
            runTestCase(DataStoreFilterComplexTest(dataStore), "doEqualsFilter")
            DataStoreGeoTest(dataStore).allTests.keys.forEach { name ->
                runTestCase(DataStoreGeoTest(dataStore), name)
            }
            runTestCase(DataStoreScanChangesTest(dataStore), "executeSimpleScanChangesRequest")
            runTestCase(DataStoreScanChangesTest(dataStore), "executeSimpleScanReversedChangesRequest")
            runTestCase(DataStoreScanChangesTest(dataStore), "executeScanChangesOnAscendingIndexRequest")
            runTestCase(DataStoreScanChangesTest(dataStore), "executeScanChangesOnDescendingIndexRequest")
            runTestCase(DataStoreScanChangesTest(dataStore), "executeScanChangesRequestWithLimit")
            runTestCase(DataStoreScanChangesTest(dataStore), "executeScanChangesRequestWithToVersion")
            runTestCase(DataStoreScanChangesTest(dataStore), "executeScanChangesRequestWithFromVersion")
            runTestCase(DataStoreScanChangesTest(dataStore), "executeScanChangesRequestWithSelect")
            runTestCase(DataStoreScanChangesTest(dataStore), "executeScanChangesRequestWithMaxVersions")
            runTestCase(DataStoreProcessUpdateTest(dataStore), "executeProcessAddRequest")
            runTestCase(DataStoreProcessUpdateTest(dataStore), "executeProcessDeletedAddRequest")
            runTestCase(DataStoreProcessUpdateTest(dataStore), "executeProcessUpdatesRespectHardDeleteTombstone")
            runTestCase(DataStoreProcessUpdateTest(dataStore), "executeProcessChangeRequest")
            runTestCase(DataStoreProcessUpdateTest(dataStore), "executeProcessAddInChangeRequest")
            runTestCase(DataStoreProcessUpdateTest(dataStore), "executeProcessRemovalRequest")
            runTestCase(DataStoreProcessUpdateTest(dataStore), "executeProcessInitialChangesRequest")
            runTestCase(DataStoreProcessUpdateTest(dataStore), "failOnInitialValuesRequest")
            runTestCase(DataStoreProcessUpdateTest(dataStore), "failOnOrderedKeysUpdateRequest")
            runTestCase(DataStoreScanUpdateHistoryTest(dataStore), "executeScanUpdateHistoryFailsWithoutIndex")
            runTestCase(DataStoreScanUpdatesAndFlowTest(dataStore), "executeSimpleScanUpdatesRequest")
            runTestCase(DataStoreScanUpdatesAndFlowTest(dataStore), "executeOrderedScanUpdatesRequest")
            runTestCase(DataStoreScanUpdatesAndFlowTest(dataStore), "executeScanUpdatesAsFlowRequest")
            runTestCase(DataStoreScanUpdatesAndFlowTest(dataStore), "uncollectedFlowDoesNotBlockWritesOrLaterListeners")
            runTestCase(DataStoreScanUpdatesAndFlowTest(dataStore), "executeScanUpdatesAsFlowWithMutableWhereRequest")
            runTestCase(DataStoreScanUpdatesAndFlowTest(dataStore), "executeScanUpdatesIncludingInitValuesAsFlowRequest")
            runTestCase(DataStoreScanUpdatesAndFlowTest(dataStore), "executeScanUpdatesAsFlowWithSelectRequest")
            runTestCase(DataStoreScanUpdatesAndFlowTest(dataStore), "executeReversedScanUpdatesAsFlowRequest")
            runTestCase(DataStoreScanUpdatesAndFlowTest(dataStore), "executeOrderedScanUpdatesAsFlowRequest")
            runTestCase(DataStoreScanUpdatesAndFlowTest(dataStore), "executeReverseOrderedScanUpdatesAsFlowRequest")
            runTestCase(DataStoreGetTest(dataStore), "executeSimpleGetWithAggregationRequest")
            runTestCase(DataStoreGetTest(dataStore), "executeGetRequestWithSelect")
            runTestCase(DataStoreScanTest(dataStore), "executeSimpleScanRequest")
            runTestCase(DataStoreScanTest(dataStore), "executeSimpleScanWithAggregationRequest")
            runTestCase(DataStoreScanTest(dataStore), "executeSimpleScanRequestReverseOrder")
            runTestCase(DataStoreScanTest(dataStore), "executeSimpleScanReverseOrderFromAbsentStartKey")
            runTestCase(DataStoreScanTest(dataStore), "executeScanRequestWithLimit")
            runTestCase(DataStoreScanTest(dataStore), "executeScanRequestWithSelect")
            runTestCase(DataStoreScanTest(dataStore), "executeSimpleScanFilterRequest")
            runTestCase(DataStoreScanTest(dataStore), "executeSimpleScanFilterExactMatchRequest")
            runTestCase(DataStoreScanTest(dataStore), "executeSimpleScanFilterExactWrongMatchRequest")
            runTestCase(DataStoreScanWithFilterTest(dataStore), "executeSimpleScanFilterRequest")
            runTestCase(DataStoreScanOnIndexTest(dataStore), "executeSimpleIndexScanRequest")
            runTestCase(DataStoreScanOnIndexTest(dataStore), "executeSimpleIndexScanWithStartKeyRequest")
            runTestCase(DataStoreScanOnIndexTest(dataStore), "executeSimpleIndexScanRequestReverseOrder")
            runTestCase(DataStoreScanOnIndexTest(dataStore), "executeIndexScanRequestWithLimit")
            runTestCase(DataStoreScanOnIndexTest(dataStore), "executeIndexScanWithMultiRangeLimit")
            runTestCase(DataStoreScanOnIndexTest(dataStore), "executeIndexScanRequestWithSelect")
            runTestCase(DataStoreScanOnIndexTest(dataStore), "executeSimpleIndexFilterScanRequest")
            runTestCase(DataStoreScanOnIndexTest(dataStore), "executeSimpleIndexFilterGreaterScanRequest")
            runTestCase(DataStoreScanOnIndexTest(dataStore), "executeSimpleIndexFilterLessScanRequest")
            runTestCase(DataStoreScanUniqueTest(dataStore), "executeSimpleScanFilterRequest")
            runTestCase(UniqueTest(dataStore), "checkUnique")
            runTestCase(UniqueTest(dataStore), "checkUniqueAddDuplicate")
        } finally {
            dataStore.close()
        }
    }

    @Test
    fun reusesSharedUpdateHistoryTests() = runTest(timeout = indexedDbLongTestTimeout) {
        installIndexedDbForTests()

        val dataStore = IndexedDbDataStore.open(
            databaseName = "maryk-indexeddb-history-${Random.nextInt()}",
            dataModelsById = dataModelsForTests,
            keepAllVersions = true,
            keepUpdateHistoryIndex = true,
        )

        try {
            runTestCase(DataStoreGetTest(dataStore), "executeToVersionGetRequest")
            runTestCase(DataStoreScanTest(dataStore), "executeScanRequestWithToVersion")
            runTestCase(DataStoreScanOnIndexTest(dataStore), "executeIndexScanRequestWithToVersionAscending")
            runTestCase(DataStoreScanOnIndexTest(dataStore), "executeIndexScanRequestWithToVersionDescending")
            runTestCase(DataStoreScanUniqueTest(dataStore), "executeSimpleScanFilterWithToVersionRequest")
            runTestCase(DataStoreScanUniqueTest(dataStore), "executeHistoricalUniqueDoesNotMatchPrefixCollision")
            runTestCase(DataStoreScanUniqueTest(dataStore), "executeHistoricalUniqueCanIncludeSoftDeletedObject")
            runTestCase(DataStoreScanUniqueTest(dataStore), "executeHistoricalUniqueCanIncludeObjectSoftDeletedByChange")
            runTestCase(DataStoreScanChangesTest(dataStore), "executeScanChangesOnIndexRequestWithToVersion")
            runTestCase(DataStoreScanUpdateHistoryTest(dataStore), "executeScanUpdateHistoryReturnsVersionOrderedEntries")
            runTestCase(DataStoreScanUpdateHistoryTest(dataStore), "executeScanUpdateHistoryCanIncludeSoftDeleteAtHistoricVersion")
            runTestCase(DataStoreScanUpdateHistoryTest(dataStore), "executeScanUpdateHistoryCanIncludeHardDelete")
            runTestCase(DataStoreScanUpdatesAndFlowTest(dataStore), "executeSimpleScanUpdatesRequestWithUpdateHistoryIndex")
            runTestCase(DataStoreScanUpdatesAndFlowTest(dataStore), "executeScanUpdatesAsFlowRequestWithUpdateHistoryIndex")
            runTestCase(DataStoreScanUpdatesAndFlowTest(dataStore), "executeScanUpdatesAsFlowRequestWithUpdateHistoryIndexTracksNewTopKey")
            runTestCase(DataStoreScanUpdatesAndFlowTest(dataStore), "executeScanUpdatesAsFlowRequestWithUpdateHistoryIndexStartKey")
            runTestCase(DataStoreScanUpdatesAndFlowTest(dataStore), "executeScanUpdatesAsFlowRequestWithUpdateHistoryIndexRefillsAfterDeletion")
        } finally {
            dataStore.close()
        }
    }

    @Test
    fun reusesSharedAdvancedIndexTests() = runTest(timeout = indexedDbLongTestTimeout) {
        installIndexedDbForTests()

        val dataStore = IndexedDbDataStore.open(
            databaseName = "maryk-indexeddb-advanced-index-${Random.nextInt()}",
            dataModelsById = dataModelsForTests,
            keepAllVersions = true,
        )

        try {
            DataStoreScanOnIndexWithPersonTest(dataStore).allTests.forEach { (name, _) ->
                runTestCase(DataStoreScanOnIndexWithPersonTest(dataStore), name)
            }
            DataStoreScanOnAnyValueIndexTest(dataStore).allTests.forEach { (name, _) ->
                runTestCase(DataStoreScanOnAnyValueIndexTest(dataStore), name)
            }
            DataStoreScanOnNormalizeIndexTest(dataStore).allTests.forEach { (name, _) ->
                runTestCase(DataStoreScanOnNormalizeIndexTest(dataStore), name)
            }
            DataStoreScanMultiTypeTest(dataStore).allTests.forEach { (name, _) ->
                runTestCase(DataStoreScanMultiTypeTest(dataStore), name)
            }
            DataStoreScanWithMutableValueIndexTest(dataStore).allTests.forEach { (name, _) ->
                runTestCase(DataStoreScanWithMutableValueIndexTest(dataStore), name)
            }
        } finally {
            dataStore.close()
        }
    }

    @Test
    fun filteredTableScanLimitCountsMatchedRows() = runTest {
        installIndexedDbForTests()

        val dataStore = IndexedDbDataStore.open(
            databaseName = "maryk-indexeddb-filter-limit-${Random.nextInt()}",
            dataModelsById = dataModelsForTests,
        )
        val keys = mutableListOf<Key<TestMarykModel>>()

        try {
            val objects = Array(6) { index ->
                TestMarykModel.create {
                    string with "haha-miss-$index"
                    int with 0
                    uint with index.toUInt()
                    double with index.toDouble()
                    dateTime with LocalDateTime(2021, 1, index + 1, 12, 0)
                    bool with false
                }
            }

            val add = dataStore.execute(TestMarykModel.add(*objects))
            add.statuses.forEach { status ->
                keys += assertStatusIs<AddSuccess<TestMarykModel>>(status).key
            }

            val matchingKeys = keys.sortedWith { first, second -> first.bytes compareToBytes second.bytes }.takeLast(2)
            matchingKeys.forEach { key ->
                val change = dataStore.execute(
                    TestMarykModel.change(
                        key.change(Change(TestMarykModel.string.ref() with "haha-match"))
                    )
                )
                assertStatusIs<ChangeSuccess<*>>(change.statuses.single())
            }

            val scan = dataStore.execute(
                TestMarykModel.scan(
                    where = Equals(TestMarykModel.string.ref() with "haha-match"),
                    limit = 2u,
                    allowTableScan = true,
                )
            )

            assertEquals(2, scan.values.size)
            assertEquals(matchingKeys.toSet(), scan.values.map { it.key }.toSet())
        } finally {
            if (keys.isNotEmpty()) {
                dataStore.execute(TestMarykModel.delete(*keys.toTypedArray(), hardDelete = true))
            }
            dataStore.close()
        }
    }

    @Test
    fun filteredScanUpdateHistoryLimitCountsMatchedRows() = runTest {
        installIndexedDbForTests()

        val dataStore = IndexedDbDataStore.open(
            databaseName = "maryk-indexeddb-history-filter-limit-${Random.nextInt()}",
            dataModelsById = dataModelsForTests,
            keepAllVersions = true,
            keepUpdateHistoryIndex = true,
        )
        val keys = mutableListOf<Key<TestMarykModel>>()

        try {
            suspend fun addObject(intValue: Int) {
                val add = dataStore.execute(
                    TestMarykModel.add(
                        TestMarykModel.create {
                            string with "haha-history-filter-$intValue-${keys.size}"
                            int with intValue
                            uint with keys.size.toUInt()
                            double with keys.size.toDouble()
                            dateTime with LocalDateTime(2021, 2, keys.size + 1, 12, 0)
                            bool with false
                        }
                    )
                )
                keys += assertStatusIs<AddSuccess<TestMarykModel>>(add.statuses.single()).key
            }

            repeat(2) { addObject(5) }
            repeat(4) { addObject(0) }

            val history = dataStore.execute(
                TestMarykModel.scanUpdateHistory(
                    where = Equals(TestMarykModel.int.ref() with 5),
                    limit = 2u,
                )
            )

            assertEquals(2, history.updates.size)
            assertEquals(keys.take(2).toSet(), history.updates.filterIsInstance<AdditionUpdate<TestMarykModel>>().map { it.key }.toSet())
        } finally {
            if (keys.isNotEmpty()) {
                dataStore.execute(TestMarykModel.delete(*keys.toTypedArray(), hardDelete = true))
            }
            dataStore.close()
        }
    }

    @Test
    fun openWritesStoreMetadata() = runTest {
        installIndexedDbForTests()

        val databaseName = "maryk-indexeddb-metadata-${Random.nextInt()}"
        val dataStore = IndexedDbDataStore.open(
            databaseName = databaseName,
            dataModelsById = dataModelsForTests,
            keepAllVersions = true,
            keepUpdateHistoryIndex = true,
        )
        dataStore.close()

        val byteStore = openIndexedDbByteStore(databaseName, setOf("meta"))
        try {
            val options = byteStore.get("meta", byteArrayOf(1))?.decodeToString()
            val models = byteStore.get("meta", byteArrayOf(2))?.decodeToString()

            assertEquals(
                "schemaVersion=2\nkeepAllVersions=true\nkeepUpdateHistoryIndex=true\n",
                options,
            )
            assertEquals(true, models?.contains("TestMarykModel") == true)
        } finally {
            byteStore.close()
        }
    }

    @Test
    fun openRejectsIncompatibleStoreOptions() = runTest {
        installIndexedDbForTests()

        val databaseName = "maryk-indexeddb-metadata-options-${Random.nextInt()}"
        IndexedDbDataStore.open(
            databaseName = databaseName,
            dataModelsById = mapOf(2u to SimpleMarykModel),
            keepAllVersions = false,
            keepUpdateHistoryIndex = false,
        ).close()

        assertFailsWith<RequestException> {
            IndexedDbDataStore.open(
                databaseName = databaseName,
                dataModelsById = mapOf(2u to SimpleMarykModel),
                keepAllVersions = true,
                keepUpdateHistoryIndex = false,
            )
        }
    }

    @Test
    fun openRejectsChangedStoredModelSignature() = runTest {
        installIndexedDbForTests()

        val databaseName = "maryk-indexeddb-metadata-model-${Random.nextInt()}"
        IndexedDbDataStore.open(
            databaseName = databaseName,
            dataModelsById = mapOf(2u to SimpleMarykModel),
        ).close()

        assertFailsWith<RequestException> {
            IndexedDbDataStore.open(
                databaseName = databaseName,
                dataModelsById = mapOf(2u to CompleteMarykModel),
            )
        }
    }

    @Test
    fun openMigratesSafeAdds() = runTest {
        installIndexedDbForTests()

        val databaseName = "maryk-indexeddb-migration-safe-${Random.nextInt()}"
        var dataStore = IndexedDbDataStore.open(
            databaseName = databaseName,
            dataModelsById = mapOf(1u to ModelV1),
        )
        val add = dataStore.execute(
            ModelV1.add(ModelV1.create { value with "haha-safe" })
        )
        val key = assertIs<AddSuccess<ModelV1>>(add.statuses.single()).key
        dataStore.close()

        dataStore = IndexedDbDataStore.open(
            databaseName = databaseName,
            dataModelsById = mapOf(1u to ModelV1_1),
        )
        try {
            val get = dataStore.execute(ModelV1_1.get(Key<ModelV1_1>(key.bytes)))

            assertEquals(1, get.values.size)
            assertEquals("haha-safe", get.values.single().values { value })
        } finally {
            dataStore.close()
        }
    }

    @Test
    fun openBackfillsNewIndexRows() = runTest {
        installIndexedDbForTests()

        val databaseName = "maryk-indexeddb-migration-index-${Random.nextInt()}"
        var dataStore = IndexedDbDataStore.open(
            databaseName = databaseName,
            dataModelsById = mapOf(1u to ModelV2),
            keepAllVersions = true,
        )
        val add = dataStore.execute(
            ModelV2.add(
                ModelV2.create { value with "ha1"; newNumber with 100 },
                ModelV2.create { value with "ha2"; newNumber with 50 },
                ModelV2.create { value with "ha3"; newNumber with 3500 },
                ModelV2.create { value with "ha4"; newNumber with 1 },
            )
        )
        val keys = add.statuses.map { status -> assertIs<AddSuccess<ModelV2>>(status).key }
        val change = dataStore.execute(
            ModelV2.change(
                keys[0].change(Change(ModelV2 { newNumber::ref } with 40)),
                keys[1].change(Change(ModelV2 { newNumber::ref } with 2000)),
                keys[2].change(Change(ModelV2 { newNumber::ref } with 500)),
                keys[3].change(Change(ModelV2 { newNumber::ref } with 990)),
            )
        )
        change.statuses.forEach { status -> assertIs<ChangeSuccess<ModelV2>>(status) }
        dataStore.close()

        dataStore = IndexedDbDataStore.open(
            databaseName = databaseName,
            dataModelsById = mapOf(1u to ModelV2ExtraIndex),
            keepAllVersions = true,
        )
        try {
            val currentScan = dataStore.execute(
                ModelV2ExtraIndex.scan(order = ModelV2ExtraIndex { newNumber::ref }.ascending())
            )
            val historicScan = dataStore.execute(
                ModelV2ExtraIndex.scan(
                    order = ModelV2ExtraIndex { newNumber::ref }.descending(),
                    toVersion = ULong.MAX_VALUE,
                )
            )

            assertEquals(listOf(40, 500, 990, 2000), currentScan.values.map { it.values { newNumber } })
            assertEquals(listOf(2000, 990, 500, 40), historicScan.values.map { it.values { newNumber } })
        } finally {
            dataStore.close()
        }
    }

    @Test
    fun openRunsExplicitMigrationHandlerForUnsafeChanges() = runTest {
        installIndexedDbForTests()

        val databaseName = "maryk-indexeddb-migration-handler-${Random.nextInt()}"
        IndexedDbDataStore.open(
            databaseName = databaseName,
            dataModelsById = mapOf(1u to ModelV1),
        ).close()

        assertFailsWith<RequestException> {
            IndexedDbDataStore.open(
                databaseName = databaseName,
                dataModelsById = mapOf(1u to ModelV2),
            )
        }

        var calls = 0
        IndexedDbDataStore.open(
            databaseName = databaseName,
            dataModelsById = mapOf(1u to ModelV2),
            migrationConfiguration = MigrationConfiguration(
                migrationHandler = {
                    calls++
                    MigrationOutcome.Success
                }
            ),
        ).close()

        assertEquals(1, calls)
        IndexedDbDataStore.open(
            databaseName = databaseName,
            dataModelsById = mapOf(1u to ModelV2),
        ).close()
    }

    @Test
    fun migrationStateSurvivesVersionUpdateFailureUntilModelDefinitionIsStored() = runTest {
        installIndexedDbForTests()

        val databaseName = "maryk-indexeddb-migration-finalization-${Random.nextInt()}"
        IndexedDbDataStore.open(
            databaseName = databaseName,
            dataModelsById = mapOf(1u to ModelV1),
        ).close()

        assertFailsWith<IllegalStateException> {
            IndexedDbDataStore.open(
                databaseName = databaseName,
                dataModelsById = mapOf(1u to ModelV2),
                migrationConfiguration = MigrationConfiguration(
                    migrationHandler = { MigrationOutcome.Success },
                ),
                versionUpdateHandler = { _, _, _ -> throw IllegalStateException("checkpoint failure") },
            )
        }

        var resumedStateStatus: MigrationStateStatus? = null
        IndexedDbDataStore.open(
            databaseName = databaseName,
            dataModelsById = mapOf(1u to ModelV2),
            migrationConfiguration = MigrationConfiguration(
                migrationContractHandler = {
                    resumedStateStatus = it.previousState?.status
                    MigrationOutcome.Success
                },
            ),
        ).close()

        assertEquals(MigrationStateStatus.Running, resumedStateStatus)
        IndexedDbDataStore.open(
            databaseName = databaseName,
            dataModelsById = mapOf(1u to ModelV2),
        ).close()
    }

    @Test
    fun startupMigrationHoldsCrossTabWriteLeaseUntilReady() = runTest(timeout = indexedDbLongTestTimeout) {
        installIndexedDbForTests()

        withoutWebLocks {
            val databaseName = "maryk-indexeddb-migration-write-lease-${Random.nextInt()}"
            IndexedDbDataStore.open(
                databaseName = databaseName,
                dataModelsById = mapOf(1u to ModelV1),
            ).close()

            val concurrentWriter = openIndexedDbByteStore(databaseName, setOf("meta"))
            val migrationStarted = CompletableDeferred<Unit>()
            val continueMigration = CompletableDeferred<Unit>()
            var migratedStore: IndexedDbDataStore? = null
            try {
                val migration = async {
                    IndexedDbDataStore.open(
                        databaseName = databaseName,
                        dataModelsById = mapOf(1u to ModelV2),
                        migrationConfiguration = MigrationConfiguration(
                            migrationHandler = {
                                migrationStarted.complete(Unit)
                                continueMigration.await()
                                MigrationOutcome.Success
                            },
                        ),
                    )
                }
                migrationStarted.await()

                val write = async {
                    concurrentWriter.transaction(setOf("meta"), IndexedDbTransactionMode.READWRITE) { store ->
                        store.put("meta", byteArrayOf(99), byteArrayOf(42))
                    }
                }
                var writeWaits = 0
                while (!write.isCompleted && writeWaits++ < 1_000) {
                    delay(1)
                }
                assertFalse(write.isCompleted)

                continueMigration.complete(Unit)
                migratedStore = migration.await()
                write.await()
                assertTrue(concurrentWriter.get("meta", byteArrayOf(99))?.contentEquals(byteArrayOf(42)) == true)
            } finally {
                if (!continueMigration.isCompleted) continueMigration.complete(Unit)
                migratedStore?.close()
                concurrentWriter.close()
            }
        }
    }

    @Test
    fun cancellingStartupMigrationStopsReentrantMutationBeforeReleasingWriteLease() =
        runTest(timeout = indexedDbLongTestTimeout) {
            installIndexedDbForTests()

            withoutWebLocks {
                val databaseName = "maryk-indexeddb-migration-cancellation-${Random.nextInt()}"
                IndexedDbDataStore.open(
                    databaseName = databaseName,
                    dataModelsById = mapOf(1u to CancellationMigrationV1),
                    fieldEncryptionProvider = XorFieldEncryptionProvider(),
                ).close()

                val encryptionStarted = CompletableDeferred<Unit>()
                val releaseEncryption = CompletableDeferred<Unit>()
                val cancellationCleanupStarted = CompletableDeferred<Unit>()
                val finishCancellationCleanup = CompletableDeferred<Unit>()
                val encryptionProvider = CancellationBlockingFieldEncryptionProvider(
                    encryptionStarted = encryptionStarted,
                    releaseEncryption = releaseEncryption,
                    cancellationCleanupStarted = cancellationCleanupStarted,
                    finishCancellationCleanup = finishCancellationCleanup,
                )
                var callbackStore: IndexedDbDataStore? = null
                val migration = async {
                    IndexedDbDataStore.open(
                        databaseName = databaseName,
                        dataModelsById = mapOf(1u to CancellationMigrationV2),
                        fieldEncryptionProvider = encryptionProvider,
                        migrationConfiguration = MigrationConfiguration(
                            migrationHandler = { context ->
                                callbackStore = context.store
                                context.store.execute(
                                    CancellationMigrationV2.add(
                                        CancellationMigrationV2.create {
                                            id with Bytes(ByteArray(16) { 7 })
                                            secret with "late startup write"
                                            requiredValue with "required"
                                        }
                                    )
                                )
                                MigrationOutcome.Success
                            },
                        ),
                    )
                }
                encryptionStarted.await()

                val competingStore = openIndexedDbByteStore(databaseName, setOf("meta"))
                var competingWrite: Deferred<Unit>? = null
                try {
                    migration.cancel()
                    competingWrite = async {
                        competingStore.transaction(setOf("meta"), IndexedDbTransactionMode.READWRITE) { store ->
                            store.put("meta", byteArrayOf(101), byteArrayOf(42))
                        }
                    }

                    var waits = 0
                    while (
                        !cancellationCleanupStarted.isCompleted &&
                        !competingWrite.isCompleted &&
                        waits++ < 1_000
                    ) {
                        delay(1)
                    }

                    assertTrue(cancellationCleanupStarted.isCompleted)

                    var writeWaits = 0
                    while (!competingWrite.isCompleted && writeWaits++ < 1_000) {
                        delay(1)
                    }
                    assertFalse(competingWrite.isCompleted)

                    finishCancellationCleanup.complete(Unit)
                    assertFailsWith<CancellationException> { migration.await() }
                    competingWrite.await()
                    assertTrue(competingStore.get("meta", byteArrayOf(101))?.contentEquals(byteArrayOf(42)) == true)
                } finally {
                    releaseEncryption.complete(Unit)
                    finishCancellationCleanup.complete(Unit)
                    withContext(NonCancellable) {
                        migration.join()
                        competingWrite?.cancel()
                        callbackStore?.close()
                        competingStore.close()
                    }
                }
            }
        }

    @Test
    fun openRejectsUnboundedMigrationRetry() = runTest {
        installIndexedDbForTests()

        val databaseName = "maryk-indexeddb-migration-retry-${Random.nextInt()}"
        IndexedDbDataStore.open(
            databaseName = databaseName,
            dataModelsById = mapOf(1u to ModelV1),
        ).close()

        assertFailsWith<RequestException> {
            IndexedDbDataStore.open(
                databaseName = databaseName,
                dataModelsById = mapOf(1u to ModelV2),
                migrationConfiguration = MigrationConfiguration(
                    migrationHandler = { MigrationOutcome.Retry() }
                ),
            )
        }
    }

    @Test
    fun openAppliesBoundedMigrationRetryPolicy() = runTest {
        installIndexedDbForTests()

        val databaseName = "maryk-indexeddb-migration-bounded-retry-${Random.nextInt()}"
        IndexedDbDataStore.open(
            databaseName = databaseName,
            dataModelsById = mapOf(1u to ModelV1),
        ).close()

        var calls = 0
        IndexedDbDataStore.open(
            databaseName = databaseName,
            dataModelsById = mapOf(1u to ModelV2),
            migrationConfiguration = MigrationConfiguration(
                migrationHandler = {
                    calls++
                    if (calls == 1) MigrationOutcome.Retry() else MigrationOutcome.Success
                },
                migrationRetryPolicy = MigrationRetryPolicy(maxRetryOutcomes = 1u),
            ),
        ).close()

        assertEquals(2, calls)
    }

    @Test
    fun migrationRetryLimitSurvivesReopen() = runTest {
        installIndexedDbForTests()

        val databaseName = "maryk-indexeddb-migration-retry-reopen-${Random.nextInt()}"
        IndexedDbDataStore.open(
            databaseName = databaseName,
            dataModelsById = mapOf(1u to ModelV1),
        ).close()

        assertFailsWith<RequestException> {
            IndexedDbDataStore.open(
                databaseName = databaseName,
                dataModelsById = mapOf(1u to ModelV2),
                migrationConfiguration = MigrationConfiguration(
                    migrationHandler = { MigrationOutcome.Retry() },
                ),
            )
        }

        var resumedCalls = 0
        assertFailsWith<RequestException> {
            IndexedDbDataStore.open(
                databaseName = databaseName,
                dataModelsById = mapOf(1u to ModelV2),
                migrationConfiguration = MigrationConfiguration(
                    migrationHandler = {
                        resumedCalls++
                        MigrationOutcome.Success
                    },
                    migrationRetryPolicy = MigrationRetryPolicy(maxAttempts = 1u),
                ),
            )
        }
        assertEquals(0, resumedCalls)
    }

    @Test
    fun migrationRetryAttemptsArePhaseLocal() = runTest {
        installIndexedDbForTests()

        val databaseName = "maryk-indexeddb-migration-phase-retry-${Random.nextInt()}"
        IndexedDbDataStore.open(
            databaseName = databaseName,
            dataModelsById = mapOf(1u to ModelV1),
        ).close()

        val backfillAttempts = mutableListOf<UInt>()
        var expandCalls = 0
        IndexedDbDataStore.open(
            databaseName = databaseName,
            dataModelsById = mapOf(1u to ModelV2),
            migrationConfiguration = MigrationConfiguration(
                migrationExpandHandler = {
                    expandCalls++
                    if (expandCalls == 1) MigrationOutcome.Retry() else MigrationOutcome.Success
                },
                migrationHandler = {
                    backfillAttempts += it.attempt
                    MigrationOutcome.Success
                },
                migrationRetryPolicy = MigrationRetryPolicy(maxRetryOutcomes = 1u),
            ),
        ).close()

        assertEquals(listOf(1u), backfillAttempts)
    }

    @Test
    fun partialMigrationResumesAfterReopen() = runTest {
        installIndexedDbForTests()

        val databaseName = "maryk-indexeddb-migration-partial-${Random.nextInt()}"
        IndexedDbDataStore.open(
            databaseName = databaseName,
            dataModelsById = mapOf(1u to ModelV1),
        ).close()

        val cursor = byteArrayOf(1, 2, 3, 4)
        assertFailsWith<RequestException> {
            IndexedDbDataStore.open(
                databaseName = databaseName,
                dataModelsById = mapOf(1u to ModelV2),
                migrationConfiguration = MigrationConfiguration(
                    migrationHandler = {
                        assertEquals(null, it.previousState)
                        MigrationOutcome.Partial(cursor, "continue after reopen")
                    },
                ),
            )
        }

        var resumed = false
        IndexedDbDataStore.open(
            databaseName = databaseName,
            dataModelsById = mapOf(1u to ModelV2),
            migrationConfiguration = MigrationConfiguration(
                migrationHandler = {
                    resumed = true
                    assertEquals(cursor.toList(), it.previousState?.cursor?.toList())
                    assertEquals(2u, it.attempt)
                    MigrationOutcome.Success
                },
            ),
        ).close()

        assertTrue(resumed)
        IndexedDbDataStore.open(
            databaseName = databaseName,
            dataModelsById = mapOf(1u to ModelV2),
        ).close()
    }

    @Test
    fun interruptedMigrationResumesWithRunningState() = runTest {
        installIndexedDbForTests()

        val databaseName = "maryk-indexeddb-migration-interrupted-${Random.nextInt()}"
        IndexedDbDataStore.open(
            databaseName = databaseName,
            dataModelsById = mapOf(1u to ModelV1),
        ).close()

        assertFailsWith<IllegalStateException> {
            IndexedDbDataStore.open(
                databaseName = databaseName,
                dataModelsById = mapOf(1u to ModelV2),
                migrationConfiguration = MigrationConfiguration(
                    migrationExpandHandler = { throw IllegalStateException("interrupted") },
                ),
            )
        }

        var resumedStateStatus: MigrationStateStatus? = null
        var resumedAttempt: UInt? = null
        IndexedDbDataStore.open(
            databaseName = databaseName,
            dataModelsById = mapOf(1u to ModelV2),
            migrationConfiguration = MigrationConfiguration(
                migrationExpandHandler = {
                    resumedStateStatus = it.previousState?.status
                    resumedAttempt = it.attempt
                    MigrationOutcome.Success
                },
                migrationHandler = { MigrationOutcome.Success },
            ),
        ).close()

        assertEquals(MigrationStateStatus.Running, resumedStateStatus)
        assertEquals(2u, resumedAttempt)
    }

    @Test
    fun fatalMigrationKeepsTheLastCursorForDiagnosis() = runTest {
        installIndexedDbForTests()

        val databaseName = "maryk-indexeddb-migration-fatal-${Random.nextInt()}"
        IndexedDbDataStore.open(
            databaseName = databaseName,
            dataModelsById = mapOf(1u to ModelV1),
        ).close()

        val cursor = byteArrayOf(5, 6, 7)
        assertFailsWith<RequestException> {
            IndexedDbDataStore.open(
                databaseName = databaseName,
                dataModelsById = mapOf(1u to ModelV2),
                migrationConfiguration = MigrationConfiguration(
                    migrationHandler = { MigrationOutcome.Partial(cursor) },
                ),
            )
        }
        assertFailsWith<RequestException> {
            IndexedDbDataStore.open(
                databaseName = databaseName,
                dataModelsById = mapOf(1u to ModelV2),
                migrationConfiguration = MigrationConfiguration(
                    migrationHandler = { MigrationOutcome.Fatal("cannot continue") },
                ),
            )
        }

        var resumedStateStatus: MigrationStateStatus? = null
        var resumedCursor: ByteArray? = null
        assertFailsWith<RequestException> {
            IndexedDbDataStore.open(
                databaseName = databaseName,
                dataModelsById = mapOf(1u to ModelV2),
                migrationConfiguration = MigrationConfiguration(
                    migrationHandler = {
                        resumedStateStatus = it.previousState?.status
                        resumedCursor = it.previousState?.cursor
                        MigrationOutcome.Fatal("still cannot continue")
                    },
                ),
            )
        }

        assertEquals(MigrationStateStatus.Failed, resumedStateStatus)
        assertEquals(cursor.toList(), resumedCursor?.toList())
    }

    @Test
    fun sensitivePropertyStoredEncrypted() = runTest {
        installIndexedDbForTests()

        val databaseName = "maryk-indexeddb-encryption-${Random.nextInt()}"
        val dataStore = IndexedDbDataStore.open(
            databaseName = databaseName,
            dataModelsById = mapOf(901u to SensitiveRecord),
            fieldEncryptionProvider = XorFieldEncryptionProvider(),
        )

        try {
            val addResult = dataStore.execute(
                SensitiveRecord.add(
                    SensitiveRecord(Bytes(ByteArray(16) { it.toByte() }), "hello", "top-secret")
                )
            )
            val addStatus = assertIs<AddSuccess<SensitiveRecord>>(addResult.statuses.single())
            val key = addStatus.key
            val get = dataStore.execute(SensitiveRecord.get(key))
            assertEquals("top-secret", get.values.single().values[SensitiveRecord.secret.ref()])
        } finally {
            dataStore.close()
        }

        val byteStore = openIndexedDbByteStore(databaseName, setOf("t:901"))
        try {
            val sensitiveRef = SensitiveRecord.secret.ref().toStorageByteArray()
            val rawStored = byteStore.get("t:901", createTableRowKey(ByteArray(16) { it.toByte() }, sensitiveRef))
            assertNotNull(rawStored)

            val plain = SensitiveRecord.secret.definition.toStorageBytes("top-secret", TypeIndicator.NoTypeIndicator.byte)
            assertFalse(rawStored.contentEquals(plain))
            assertTrue(rawStored.copyOfRange(0, 4).contentEquals("MKE2".encodeToByteArray()))
        } finally {
            byteStore.close()
        }
    }

    @Test
    fun sensitivePropertyReadsLegacyMke1Snapshot() = runTest {
        installIndexedDbForTests()

        val databaseName = "maryk-indexeddb-encryption-legacy-${Random.nextInt()}"
        val provider = XorFieldEncryptionProvider()
        val dataStore = IndexedDbDataStore.open(
            databaseName = databaseName,
            dataModelsById = mapOf(901u to SensitiveRecord),
            fieldEncryptionProvider = provider,
            keepAllVersions = true,
        )

        try {
            val addResult = dataStore.execute(
                SensitiveRecord.add(SensitiveRecord(Bytes(ByteArray(16) { it.toByte() }), "hello", "top-secret"))
            )
            val addStatus = assertIs<AddSuccess<SensitiveRecord>>(addResult.statuses.single())
            val key = addStatus.key
            val qualifier = SensitiveRecord.secret.ref().toStorageByteArray()
            val plain = SensitiveRecord.secret.definition.toStorageBytes("top-secret", TypeIndicator.NoTypeIndicator.byte)
            val legacyValue = combineToByteArray(
                FieldEncryptionEnvelope.Legacy.magic,
                provider.encrypt(plain),
            )
            val snapshot = requireNotNull(dataStore.byteStore.get("k:901", key.bytes))
            val (meta, rows) = decodeCurrentSnapshot(snapshot)
            dataStore.byteStore.put(
                "k:901",
                key.bytes,
                encodeCurrentSnapshot(meta, rows.map { (reference, value) ->
                    reference to if (reference.contentEquals(qualifier)) legacyValue else value
                }),
            )
            dataStore.byteStore.put("t:901", createTableRowKey(key.bytes, qualifier), legacyValue)

            val historicKey = createHistoricSnapshotRowKey(key.bytes, addStatus.version)
            val historicSnapshot = requireNotNull(dataStore.byteStore.get("ht:901", historicKey))
            val (historicMeta, historicRows) = decodeHistoricSnapshot(historicSnapshot)
            dataStore.byteStore.put(
                "ht:901",
                historicKey,
                encodeHistoricSnapshot(historicMeta, historicRows.map { (reference, value) ->
                    reference to if (reference.contentEquals(qualifier)) legacyValue else value
                }),
            )

            val get = dataStore.execute(SensitiveRecord.get(key))
            assertEquals("top-secret", get.values.single().values[SensitiveRecord.secret.ref()])
            val historicGet = dataStore.execute(SensitiveRecord.get(key, toVersion = addStatus.version))
            assertEquals("top-secret", historicGet.values.single().values[SensitiveRecord.secret.ref()])
        } finally {
            dataStore.close()
        }
    }

    @Test
    fun nonSensitiveMkePrefixValuesRoundTripWithAndWithoutProvider() = runTest {
        installIndexedDbForTests()

        listOf<XorFieldEncryptionProvider?>(null, XorFieldEncryptionProvider()).forEachIndexed { providerIndex, provider ->
            val dataStore = IndexedDbDataStore.open(
                databaseName = "maryk-indexeddb-non-sensitive-mke-prefix-$providerIndex-${Random.nextInt()}",
                dataModelsById = mapOf(905u to MkePrefixRecord),
                fieldEncryptionProvider = provider,
            )
            try {
                FieldEncryptionEnvelope.entries.forEachIndexed { envelopeIndex, envelope ->
                    val text = envelope.magic.decodeToString() + " public text"
                    val bytes = Bytes(envelope.magic + byteArrayOf(1, 2, 3, 4))
                    val key = assertIs<AddSuccess<MkePrefixRecord>>(
                        dataStore.execute(
                            MkePrefixRecord.add(
                                MkePrefixRecord(
                                    Bytes(ByteArray(16) { envelopeIndex.toByte() }),
                                    text,
                                    bytes,
                                )
                            )
                        ).statuses.single()
                    ).key

                    val values = dataStore.execute(MkePrefixRecord.get(key)).values.single()
                    assertEquals(text, values.values { publicText })
                    assertContentEquals(bytes.bytes, requireNotNull(values.values { publicBytes }).bytes)
                    assertContentEquals(
                        text.encodeToByteArray(),
                        IndexedDbSensitiveFieldSupport(mapOf(905u to MkePrefixRecord), provider).decryptValueIfNeeded(
                            905u,
                            key.bytes,
                            MkePrefixRecord.publicText.ref().toStorageByteArray(),
                            text.encodeToByteArray(),
                        ),
                    )
                    assertContentEquals(
                        bytes.bytes,
                        IndexedDbSensitiveFieldSupport(mapOf(905u to MkePrefixRecord), provider).decryptValueIfNeeded(
                            905u,
                            key.bytes,
                            MkePrefixRecord.publicBytes.ref().toStorageByteArray(),
                            bytes.bytes,
                        ),
                    )
                }
            } finally {
                dataStore.close()
            }
        }
    }

    @Test
    fun sensitivePropertyRequiresEncryptionProvider() = runTest {
        installIndexedDbForTests()

        assertFailsWith<RequestException> {
            IndexedDbDataStore.open(
                databaseName = "maryk-indexeddb-encryption-missing-${Random.nextInt()}",
                dataModelsById = mapOf(901u to SensitiveRecord),
            )
        }
    }

    @Test
    fun sensitivePropertyCannotBeIndexed() = runTest {
        installIndexedDbForTests()

        assertFailsWith<RequestException> {
            IndexedDbDataStore.open(
                databaseName = "maryk-indexeddb-encryption-indexed-${Random.nextInt()}",
                dataModelsById = mapOf(902u to SensitiveIndexedRecord),
                fieldEncryptionProvider = XorFieldEncryptionProvider(),
            )
        }
    }

    @Test
    fun sensitiveUniqueRequiresTokenProvider() = runTest {
        installIndexedDbForTests()

        assertFailsWith<RequestException> {
            IndexedDbDataStore.open(
                databaseName = "maryk-indexeddb-encryption-unique-provider-${Random.nextInt()}",
                dataModelsById = mapOf(904u to SensitiveUniqueRecord),
                fieldEncryptionProvider = XorFieldEncryptionProvider(),
            )
        }
    }

    @Test
    fun sensitiveUniqueUsesDeterministicToken() = runTest {
        installIndexedDbForTests()

        val dataStore = IndexedDbDataStore.open(
            databaseName = "maryk-indexeddb-encryption-unique-${Random.nextInt()}",
            dataModelsById = mapOf(904u to SensitiveUniqueRecord),
            fieldEncryptionProvider = XorWithTokenFieldEncryptionProvider(),
        )

        try {
            val firstResult: IsAddResponseStatus<SensitiveUniqueRecord> = dataStore.execute(
                SensitiveUniqueRecord.add(
                    SensitiveUniqueRecord(Bytes(ByteArray(16) { 1 }), "same-secret")
                )
            ).statuses.single()
            assertIs<AddSuccess<SensitiveUniqueRecord>>(firstResult)

            val secondResult: IsAddResponseStatus<SensitiveUniqueRecord> = dataStore.execute(
                SensitiveUniqueRecord.add(
                    SensitiveUniqueRecord(Bytes(ByteArray(16) { 2 }), "same-secret")
                )
            ).statuses.single()
            assertIs<ValidationFail<SensitiveUniqueRecord>>(secondResult)
        } finally {
            dataStore.close()
        }
    }

    @Test
    fun sensitiveUniqueDoesNotWritePreviousRotationToken() = runTest {
        installIndexedDbForTests()

        val databaseName = "maryk-indexeddb-encryption-rotation-${Random.nextInt()}"
        val rotatingStore = IndexedDbDataStore.open(
            databaseName = databaseName,
            dataModelsById = mapOf(904u to SensitiveUniqueRecord),
            fieldEncryptionProvider = RotatingTokenFieldEncryptionProvider(2, listOf(1)),
        )
        try {
            val result: IsAddResponseStatus<SensitiveUniqueRecord> = rotatingStore.execute(
                SensitiveUniqueRecord.add(SensitiveUniqueRecord(Bytes(ByteArray(16) { 1 }), "same-secret"))
            ).statuses.single()
            assertIs<AddSuccess<SensitiveUniqueRecord>>(result)
        } finally {
            rotatingStore.close()
        }

        val previousKeyStore = IndexedDbDataStore.open(
            databaseName = databaseName,
            dataModelsById = mapOf(904u to SensitiveUniqueRecord),
            fieldEncryptionProvider = RotatingTokenFieldEncryptionProvider(1),
        )
        try {
            val result: IsAddResponseStatus<SensitiveUniqueRecord> = previousKeyStore.execute(
                SensitiveUniqueRecord.add(SensitiveUniqueRecord(Bytes(ByteArray(16) { 2 }), "same-secret"))
            ).statuses.single()
            assertIs<AddSuccess<SensitiveUniqueRecord>>(result)
        } finally {
            previousKeyStore.close()
        }
    }

    @Test
    fun softDeleteRemovesRetainedSensitiveUniqueTokenAndHistoriesOnlyItsExistingRow() = runTest {
        installIndexedDbForTests()

        val databaseName = "maryk-indexeddb-encryption-rotation-delete-${Random.nextInt()}"
        val oldStore = IndexedDbDataStore.open(
            databaseName = databaseName,
            dataModelsById = mapOf(904u to SensitiveUniqueRecord),
            keepAllVersions = true,
            fieldEncryptionProvider = RotatingTokenFieldEncryptionProvider(1),
        )
        val key = try {
            assertIs<AddSuccess<SensitiveUniqueRecord>>(
                oldStore.execute(
                    SensitiveUniqueRecord.add(SensitiveUniqueRecord(Bytes(ByteArray(16) { 1 }), "same-secret"))
                ).statuses.single()
            ).key
        } finally {
            oldStore.close()
        }

        val rotatedStore = IndexedDbDataStore.open(
            databaseName = databaseName,
            dataModelsById = mapOf(904u to SensitiveUniqueRecord),
            keepAllVersions = true,
            fieldEncryptionProvider = RotatingTokenFieldEncryptionProvider(2, listOf(1)),
        )
        val deleteVersion = try {
            assertIs<DeleteSuccess<SensitiveUniqueRecord>>(
                rotatedStore.execute(SensitiveUniqueRecord.delete(key)).statuses.single()
            ).version
        } finally {
            rotatedStore.close()
        }

        val historicUniqueStore = openIndexedDbByteStore(databaseName, setOf("hu:904"))
        try {
            val reference = SensitiveUniqueRecord.secret.ref().toStorageByteArray()
            val retainedTokenKey = createUniqueRowKey(reference, ByteArray(16) { 1 })
            val activeTokenKey = createUniqueRowKey(reference, ByteArray(16) { 2 })
            assertNotNull(historicUniqueStore.get("hu:904", createHistoricVersionedRowKey(retainedTokenKey, deleteVersion)))
            assertEquals(null, historicUniqueStore.get("hu:904", createHistoricVersionedRowKey(activeTokenKey, deleteVersion)))
        } finally {
            historicUniqueStore.close()
        }

        val replacementStore = IndexedDbDataStore.open(
            databaseName = databaseName,
            dataModelsById = mapOf(904u to SensitiveUniqueRecord),
            keepAllVersions = true,
            fieldEncryptionProvider = RotatingTokenFieldEncryptionProvider(2, listOf(1)),
        )
        try {
            assertIs<AddSuccess<SensitiveUniqueRecord>>(
                replacementStore.execute(
                    SensitiveUniqueRecord.add(SensitiveUniqueRecord(Bytes(ByteArray(16) { 2 }), "same-secret"))
                ).statuses.single()
            )
        } finally {
            replacementStore.close()
        }
    }

    @Test
    fun indexedScanLimitCountsMatchedRowsAfterSoftDeleteSkips() = runTest(timeout = indexedDbLongTestTimeout) {
        installIndexedDbForTests()

        val dataStore = IndexedDbDataStore.open(
            databaseName = "maryk-indexeddb-index-limit-${Random.nextInt()}",
            dataModelsById = dataModelsForTests,
        )
        val keys = mutableListOf<Key<Person>>()

        try {
            val persons = Array(258) { index ->
                Person.create {
                    firstName with "A${index.toString().padStart(3, '0')}"
                    surname with "Paged"
                }
            }

            val add = dataStore.execute(Person.add(*persons))
            add.statuses.forEach { status ->
                keys += assertStatusIs<AddSuccess<Person>>(status).key
            }

            val delete = dataStore.execute(
                Person.delete(*keys.take(256).toTypedArray(), hardDelete = false)
            )
            delete.statuses.forEach { status ->
                assertStatusIs<DeleteSuccess<Person>>(status)
            }

            val scan = dataStore.execute(
                Person.scan(
                    order = Orders(Person { surname::ref }.ascending(), Person { firstName::ref }.ascending()),
                    limit = 2u,
                )
            )

            assertEquals(2, scan.values.size)
            assertEquals(listOf(keys[256], keys[257]), scan.values.map { it.key })
        } finally {
            if (keys.isNotEmpty()) {
                dataStore.execute(Person.delete(*keys.toTypedArray(), hardDelete = true))
            }
            dataStore.close()
        }
    }

    @Test
    fun hardDeletePurgesChangedHistoricUniqueRows() = runTest {
        installIndexedDbForTests()

        val databaseName = "maryk-indexeddb-hard-delete-purge-${Random.nextInt()}"
        val dataStore = IndexedDbDataStore.open(
            databaseName = databaseName,
            dataModelsById = dataModelsForTests,
            keepAllVersions = true,
            keepUpdateHistoryIndex = true,
        )

        val values = CompleteMarykModel.create {
            string with "haas"
            number with 24u
            subModel with SimpleMarykModel.create {
                value with "haha"
            }
            multi with T2(22)
            booleanForKey with true
            dateForKey with LocalDate(2018, 3, 29)
            multiForKey with S1("hii")
            enumEmbedded with E1
        }

        try {
            val add = dataStore.execute(CompleteMarykModel.add(values))
            val addStatus = assertStatusIs<AddSuccess<*>>(add.statuses.single())
            @Suppress("UNCHECKED_CAST")
            val key = addStatus.key as Key<CompleteMarykModel>

            val change = dataStore.execute(
                CompleteMarykModel.change(
                    key.change(Change(CompleteMarykModel.string.ref() with "haas2"))
                )
            )
            val changeStatus = assertStatusIs<ChangeSuccess<*>>(change.statuses.single())

            val delete = dataStore.execute(CompleteMarykModel.delete(key, hardDelete = true))
            assertStatusIs<DeleteSuccess<*>>(delete.statuses.single())

            val oldUnique = dataStore.execute(
                CompleteMarykModel.scan(
                    where = Equals(CompleteMarykModel.string.ref() with "haas"),
                    toVersion = addStatus.version,
                    filterSoftDeleted = false,
                )
            )
            val newUnique = dataStore.execute(
                CompleteMarykModel.scan(
                    where = Equals(CompleteMarykModel.string.ref() with "haas2"),
                    toVersion = changeStatus.version,
                    filterSoftDeleted = false,
                )
            )

            assertEquals(0, oldUnique.values.size)
            assertEquals(0, newUnique.values.size)

            val byteStore = openIndexedDbByteStore(databaseName, setOf("hik:5", "huk:5"))
            try {
                assertEquals(0, byteStore.scan("hik:5").size)
                assertEquals(0, byteStore.scan("huk:5").size)
            } finally {
                byteStore.close()
            }
        } finally {
            dataStore.close()
        }
    }

    @Test
    fun scanUpdateHistoryReconstructsUnserializableCreationLogs() = runTest {
        installIndexedDbForTests()

        val dataStore = IndexedDbDataStore.open(
            databaseName = "maryk-indexeddb-unserializable-creation-${Random.nextInt()}",
            dataModelsById = dataModelsForTests,
            keepAllVersions = true,
            keepUpdateHistoryIndex = true,
        )

        val values = CompleteMarykModel.create {
            string with "haas"
            number with 24u
            subModel with SimpleMarykModel.create {
                value with "haha"
            }
            multi with T2(22)
            booleanForKey with true
            dateForKey with LocalDate(2018, 3, 29)
            multiForKey with S1("hii")
            enumEmbedded with E1
        }

        try {
            val add = dataStore.execute(CompleteMarykModel.add(values))
            val addStatus = assertStatusIs<AddSuccess<*>>(add.statuses.single())

            val history = dataStore.execute(
                CompleteMarykModel.scanUpdateHistory(
                    fromVersion = addStatus.version,
                    toVersion = addStatus.version,
                    filterSoftDeleted = false,
                    limit = 1u,
                )
            )

            val update = assertIs<AdditionUpdate<*>>(history.updates.single())
            assertEquals(addStatus.key, update.key)
            assertEquals(addStatus.version, update.version)
        } finally {
            dataStore.close()
        }
    }

}

private class XorFieldEncryptionProvider : ContextualFieldEncryptionProvider {
    override suspend fun encrypt(value: ByteArray, offset: Int, length: Int): ByteArray = xor(value, offset, length)
    override suspend fun decrypt(value: ByteArray, offset: Int, length: Int): ByteArray = xor(value, offset, length)
    override suspend fun encrypt(context: FieldEncryptionContext, value: ByteArray, offset: Int, length: Int): ByteArray =
        xor(value, offset, length)
    override suspend fun decrypt(context: FieldEncryptionContext, value: ByteArray, offset: Int, length: Int): ByteArray =
        xor(value, offset, length)

    private fun xor(value: ByteArray, offset: Int, length: Int): ByteArray =
        ByteArray(length) { index -> (value[offset + index].toInt() xor 0x5A).toByte() }
}

private class CancellationBlockingFieldEncryptionProvider(
    private val encryptionStarted: CompletableDeferred<Unit>,
    private val releaseEncryption: CompletableDeferred<Unit>,
    private val cancellationCleanupStarted: CompletableDeferred<Unit>,
    private val finishCancellationCleanup: CompletableDeferred<Unit>,
) : ContextualFieldEncryptionProvider {
    override suspend fun encrypt(value: ByteArray, offset: Int, length: Int): ByteArray {
        encryptionStarted.complete(Unit)
        try {
            releaseEncryption.await()
        } finally {
            if (!currentCoroutineContext().isActive) {
                withContext(NonCancellable) {
                    cancellationCleanupStarted.complete(Unit)
                    finishCancellationCleanup.await()
                }
            }
        }
        return value.copyOfRange(offset, offset + length)
    }

    override suspend fun decrypt(value: ByteArray, offset: Int, length: Int): ByteArray =
        value.copyOfRange(offset, offset + length)
    override suspend fun encrypt(context: FieldEncryptionContext, value: ByteArray, offset: Int, length: Int): ByteArray =
        encrypt(value, offset, length)
    override suspend fun decrypt(context: FieldEncryptionContext, value: ByteArray, offset: Int, length: Int): ByteArray =
        decrypt(value, offset, length)
}

private object CancellationMigrationV1 : RootDataModel<CancellationMigrationV1>(
    name = "CancellationMigrationModel",
    version = Version(1),
    keyDefinition = { CancellationMigrationV1.id.ref() },
    minimumKeyScanByteRange = 0u,
) {
    val id by fixedBytes(1u, byteSize = 16, final = true)
    val secret by string(2u, sensitive = true)
}

private object CancellationMigrationV2 : RootDataModel<CancellationMigrationV2>(
    name = "CancellationMigrationModel",
    version = Version(2),
    keyDefinition = { CancellationMigrationV2.id.ref() },
    minimumKeyScanByteRange = 0u,
) {
    val id by fixedBytes(1u, byteSize = 16, final = true)
    val secret by string(2u, sensitive = true)
    val requiredValue by string(3u, required = true)
}

private class XorWithTokenFieldEncryptionProvider :
    ContextualFieldEncryptionProvider,
    SensitiveIndexTokenProvider {
    override suspend fun encrypt(value: ByteArray, offset: Int, length: Int): ByteArray = xor(value, offset, length)
    override suspend fun decrypt(value: ByteArray, offset: Int, length: Int): ByteArray = xor(value, offset, length)
    override suspend fun encrypt(context: FieldEncryptionContext, value: ByteArray, offset: Int, length: Int): ByteArray =
        xor(value, offset, length)
    override suspend fun decrypt(context: FieldEncryptionContext, value: ByteArray, offset: Int, length: Int): ByteArray =
        xor(value, offset, length)

    override suspend fun deriveDeterministicToken(
        modelId: UInt,
        reference: ByteArray,
        value: ByteArray,
        offset: Int,
        length: Int,
    ): ByteArray {
        val token = ByteArray(16)
        var tokenIndex = 0
        for (byte in reference) {
            token[tokenIndex % token.size] = (token[tokenIndex % token.size].toInt() xor byte.toInt() xor 0x21).toByte()
            tokenIndex++
        }
        for (index in offset until offset + length) {
            val byte = value[index]
            token[tokenIndex % token.size] = (token[tokenIndex % token.size].toInt() xor byte.toInt() xor 0x63).toByte()
            tokenIndex++
        }
        token[0] = (token[0].toInt() xor modelId.toInt()).toByte()
        return token
    }

    private fun xor(value: ByteArray, offset: Int, length: Int): ByteArray =
        ByteArray(length) { index -> (value[offset + index].toInt() xor 0x5A).toByte() }
}

private class RotatingTokenFieldEncryptionProvider(
    private val activeToken: Int,
    private val previousTokens: List<Int> = emptyList(),
) : ContextualFieldEncryptionProvider, SensitiveIndexTokenProvider {
    override suspend fun encrypt(value: ByteArray, offset: Int, length: Int): ByteArray = xor(value, offset, length)
    override suspend fun decrypt(value: ByteArray, offset: Int, length: Int): ByteArray = xor(value, offset, length)
    override suspend fun encrypt(context: FieldEncryptionContext, value: ByteArray, offset: Int, length: Int): ByteArray =
        xor(value, offset, length)
    override suspend fun decrypt(context: FieldEncryptionContext, value: ByteArray, offset: Int, length: Int): ByteArray =
        xor(value, offset, length)

    override suspend fun deriveDeterministicToken(
        modelId: UInt,
        reference: ByteArray,
        value: ByteArray,
        offset: Int,
        length: Int,
    ): ByteArray = ByteArray(16) { activeToken.toByte() }

    override suspend fun deriveDeterministicTokenCandidates(
        modelId: UInt,
        reference: ByteArray,
        value: ByteArray,
        offset: Int,
        length: Int,
    ): List<ByteArray> = (listOf(activeToken) + previousTokens)
        .distinct()
        .map { token -> ByteArray(16) { token.toByte() } }

    private fun xor(value: ByteArray, offset: Int, length: Int): ByteArray =
        ByteArray(length) { index -> (value[offset + index].toInt() xor 0x5A).toByte() }
}

private object SensitiveRecord : RootDataModel<SensitiveRecord>(
    keyDefinition = { SensitiveRecord.id.ref() },
    minimumKeyScanByteRange = 0u,
) {
    val id by fixedBytes(1u, byteSize = 16, final = true)
    val publicText by string(2u)
    val secret by string(3u, sensitive = true)

    operator fun invoke(id: Bytes, publicText: String, secret: String) = create {
        this.id with id
        this.publicText with publicText
        this.secret with secret
    }
}

private object MkePrefixRecord : RootDataModel<MkePrefixRecord>(
    keyDefinition = { MkePrefixRecord.id.ref() },
    minimumKeyScanByteRange = 0u,
) {
    val id by fixedBytes(1u, byteSize = 16, final = true)
    val publicText by string(2u)
    val publicBytes by fixedBytes(3u, byteSize = 8)

    operator fun invoke(id: Bytes, publicText: String, publicBytes: Bytes) = create {
        this.id with id
        this.publicText with publicText
        this.publicBytes with publicBytes
    }
}

private object SensitiveIndexedRecord : RootDataModel<SensitiveIndexedRecord>(
    keyDefinition = { SensitiveIndexedRecord.id.ref() },
    indexes = { listOf(SensitiveIndexedRecord.secret.ref()) },
    minimumKeyScanByteRange = 0u,
) {
    val id by fixedBytes(1u, byteSize = 16, final = true)
    val secret by string(2u, sensitive = true)
}

private object SensitiveUniqueRecord : RootDataModel<SensitiveUniqueRecord>(
    keyDefinition = { SensitiveUniqueRecord.id.ref() },
    minimumKeyScanByteRange = 0u,
) {
    val id by fixedBytes(1u, byteSize = 16, final = true)
    val secret by string(2u, unique = true, sensitive = true)

    operator fun invoke(id: Bytes, secret: String) = create {
        this.id with id
        this.secret with secret
    }
}

private val indexedDbLongTestTimeout = 3.minutes

private infix fun ByteArray.compareToBytes(other: ByteArray): Int {
    val size = minOf(size, other.size)
    for (index in 0 until size) {
        val comparison = (this[index].toInt() and 0xff) - (other[index].toInt() and 0xff)
        if (comparison != 0) return comparison
    }
    return this.size - other.size
}

private suspend fun runTestCase(
    testCase: IsDataStoreTest,
    name: String,
) {
    val test = testCase.allTests[name] ?: error("Missing datastore test `$name`.")
    testCase.initData()
    try {
        test()
    } catch (e: Throwable) {
        throw AssertionError("Shared datastore test `$name` failed: $e", e)
    } finally {
        testCase.resetData()
    }
}
