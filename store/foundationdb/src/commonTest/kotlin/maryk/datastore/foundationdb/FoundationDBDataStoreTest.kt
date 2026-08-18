package maryk.datastore.foundationdb

import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.produceIn
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.datetime.LocalDateTime
import maryk.core.exceptions.RequestException
import maryk.core.properties.types.Key
import maryk.core.properties.types.invoke
import maryk.core.query.changes.Change
import maryk.core.query.changes.Check
import maryk.core.query.changes.change
import maryk.core.query.pairs.with
import maryk.core.query.requests.add
import maryk.core.query.requests.change
import maryk.core.query.requests.delete
import maryk.core.query.requests.get
import maryk.core.query.requests.scan
import maryk.core.query.responses.UpdateResponse
import maryk.core.query.responses.updates.AdditionUpdate
import maryk.core.query.responses.updates.InitialValuesUpdate
import maryk.core.query.responses.statuses.AddSuccess
import maryk.core.query.responses.statuses.DoesNotExist
import maryk.core.query.responses.statuses.ValidationFail
import maryk.datastore.shared.DataStoreBackupChunk
import maryk.datastore.shared.DataStoreBackupManifest
import maryk.datastore.shared.DataStoreBackupWriter
import maryk.datastore.shared.backup
import maryk.datastore.test.dataModelsForTests
import maryk.datastore.test.runDataStoreTests
import maryk.datastore.test.DataStoreBackupRoundTripTest
import maryk.datastore.test.DurableReplicationTombstoneTest
import maryk.datastore.test.UniqueModel
import maryk.datastore.test.UniqueOwnershipTest
import maryk.datastore.foundationdb.processors.helpers.awaitResult
import maryk.datastore.foundationdb.processors.helpers.packKey
import maryk.test.models.Log
import maryk.test.models.SimpleMarykModel
import maryk.test.models.TestMarykModel
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.uuid.Uuid

class FoundationDBDataStoreTest {
    @Test
    fun replicationTombstoneSurvivesReopen() = runTest(timeout = 3.minutes) {
        val directoryPath = listOf("maryk", "test", "durable-replication-tombstones", Uuid.random().toString())
        var dataStore = FoundationDBDataStore.open(
            directoryPath = directoryPath,
            dataModelsById = dataModelsForTests,
            keepAllVersions = false,
        )
        try {
            val test = DurableReplicationTombstoneTest()
            test.writeHardDelete(dataStore)
            dataStore.close()
            dataStore = FoundationDBDataStore.open(
                directoryPath = directoryPath,
                dataModelsById = dataModelsForTests,
                keepAllVersions = false,
            )
            test.replayStaleAdd(dataStore)
        } finally {
            dataStore.close()
        }
    }

    @Test
    fun replicatedUpdatesRespectHardDeleteTombstonesWithoutUpdateHistory() = runTest(timeout = 3.minutes) {
        val dataStore = FoundationDBDataStore.open(
            directoryPath = listOf("maryk", "test", "replication-tombstones", Uuid.random().toString()),
            dataModelsById = dataModelsForTests,
            keepAllVersions = false,
            keepUpdateHistoryIndex = false,
        )
        try {
            runDataStoreTests(dataStore, "executeProcessUpdatesRespectHardDeleteTombstone")
        } finally {
            dataStore.close()
        }
    }

    @Test
    fun failedCompoundCheckDoesNotCommitLaterChange() = runTest(timeout = 3.minutes) {
        val dataStore = FoundationDBDataStore.open(
            directoryPath = listOf("maryk", "test", "compound-check-atomicity", Uuid.random().toString()),
            dataModelsById = dataModelsForTests,
        )

        try {
            val key = assertIs<AddSuccess<TestMarykModel>>(
                dataStore.execute(
                    TestMarykModel.add(TestMarykModel.create {
                        string with "happy"
                        int with 1
                        uint with 1u
                        double with 1.0
                        dateTime with LocalDateTime(2020, 1, 1, 0, 0)
                        bool with false
                        list with listOf(1, 2)
                    })
                ).statuses.single()
            ).key

            assertIs<ValidationFail<*>>(
                dataStore.execute(
                    TestMarykModel.change(
                        key.change(
                            Check(TestMarykModel { list::ref } with listOf(3)),
                            Change(TestMarykModel { string::ref } with "happier"),
                        )
                    )
                ).statuses.single()
            )
            assertEquals(
                "happy",
                dataStore.execute(TestMarykModel.get(key)).values.single().values { string }
            )
        } finally {
            dataStore.close()
        }
    }

    @Test
    fun uniqueOwnershipHonorsSoftDeletesAndRestoreCollisions() = runTest(timeout = 3.minutes) {
        val dataStore = FoundationDBDataStore.open(
            directoryPath = listOf("maryk", "test", "unique-soft-delete-ownership", Uuid.random().toString()),
            dataModelsById = mapOf(6u to UniqueModel),
            keepAllVersions = true,
        )

        try {
            UniqueOwnershipTest(dataStore).finalSoftDeleteWithCollidingUniqueReleasesOwnership()
            UniqueOwnershipTest(dataStore).deletedUniqueMutationDoesNotClaimOwnership()
            UniqueOwnershipTest(dataStore).restoreCollisionKeepsDeletedRecordOwnerless()
        } finally {
            dataStore.close()
        }
    }

    @Test
    fun abortedDeleteAttemptDoesNotEmitDeletion() = runTest(timeout = 3.minutes) {
        withContext(Dispatchers.Default) {
            val store = FoundationDBDataStore.open(
                directoryPath = listOf("maryk", "test", "aborted-delete-emission", Uuid.random().toString()),
                dataModelsById = mapOf(1u to SimpleMarykModel),
            )
            try {
                val key = assertIs<AddSuccess<SimpleMarykModel>>(
                    store.execute(SimpleMarykModel.add(SimpleMarykModel.create { value with "happy delete" })).statuses.single()
                ).key
                val updates = store.executeFlow(SimpleMarykModel.scan(allowTableScan = true)).produceIn(this)
                try {
                    assertIs<InitialValuesUpdate<SimpleMarykModel>>(withTimeout(10_000) { updates.receive() })

                    store.afterDeleteUpdatePrepared.value = {
                        store.runTransaction { conflictingTransaction ->
                            conflictingTransaction.clear(packKey(store.getTableDirs(SimpleMarykModel).keysPrefix, key.bytes))
                        }
                    }

                    assertIs<DoesNotExist<SimpleMarykModel>>(
                        store.execute(SimpleMarykModel.delete(key, hardDelete = true)).statuses.single()
                    )
                    assertNull(withTimeoutOrNull(500) { updates.receive() })
                } finally {
                    updates.cancel()
                }
            } finally {
                store.afterDeleteUpdatePrepared.value = null
                store.close()
            }
        }
    }

    @Test
    fun rejectsReplicatedAdditionWithDifferentFirstVersion() = runTest(timeout = 3.minutes) {
        val store = FoundationDBDataStore.open(
            directoryPath = listOf("maryk", "test", "invalid-addition-update", Uuid.random().toString()),
            dataModelsById = mapOf(1u to SimpleMarykModel),
        )
        val values = SimpleMarykModel.create { value with "invalid replicated addition" }

        try {
            assertFailsWith<RequestException> {
                store.processUpdate(
                    UpdateResponse(
                        SimpleMarykModel,
                        AdditionUpdate(
                            key = Key(ByteArray(16)),
                            version = 2uL,
                            firstVersion = 1uL,
                            insertionIndex = 0,
                            isDeleted = false,
                            values = values,
                        )
                    )
                )
            }
            assertTrue(store.execute(SimpleMarykModel.scan(allowTableScan = true)).values.isEmpty())
        } finally {
            store.close()
        }
    }

    @Test
    fun localUpdateEmissionCompletesBeforeMutationResponse() = runTest(timeout = 3.minutes) {
        withContext(Dispatchers.Default) {
            val dataStore = FoundationDBDataStore.open(
                fdbClusterFilePath = "./fdb.cluster",
                directoryPath = listOf("maryk", "test", "delayed-flow-update", Uuid.random().toString()),
                dataModelsById = mapOf(1u to SimpleMarykModel),
            )
            val emissionStarted = CompletableDeferred<Unit>()
            val releaseEmission = CompletableDeferred<Unit>()
            val delayFirstEmission = atomic(true)
            try {
                dataStore.beforeUpdateEmission.value = {
                    if (delayFirstEmission.getAndSet(false)) {
                        emissionStarted.complete(Unit)
                        releaseEmission.await()
                    }
                }
                val initialValues = SimpleMarykModel.create { value with "happy initial snapshot" }
                val firstAdd = async {
                    dataStore.execute(SimpleMarykModel.add(initialValues))
                }
                withTimeout(10_000) { emissionStarted.await() }
                assertFalse(firstAdd.isCompleted)
                releaseEmission.complete(Unit)
                assertIs<AddSuccess<SimpleMarykModel>>(firstAdd.await().statuses.single())

                val updates = dataStore.executeFlow(
                    SimpleMarykModel.scan(allowTableScan = true)
                ).produceIn(this)
                try {
                    assertIs<InitialValuesUpdate<SimpleMarykModel>>(withTimeout(10_000) { updates.receive() }).also {
                        assertEquals("happy initial snapshot", it.values.single().values { value })
                    }

                    dataStore.execute(
                        SimpleMarykModel.add(SimpleMarykModel.create { value with "happy after snapshot" })
                    )

                    assertIs<AdditionUpdate<SimpleMarykModel>>(withTimeout(10_000) { updates.receive() }).also {
                        assertEquals("happy after snapshot", it.values { value })
                    }
                } finally {
                    updates.cancel()
                }
            } finally {
                dataStore.beforeUpdateEmission.value = null
                releaseEmission.complete(Unit)
                dataStore.close()
            }
        }
    }

    @Test
    fun portableBackupRoundTrip() = runTest(timeout = 3.minutes) {
        val models = mapOf(1u to SimpleMarykModel)
        val source = FoundationDBDataStore.open(
            directoryPath = listOf("maryk", "test", "backup-source", Uuid.random().toString()),
            dataModelsById = models,
            keepAllVersions = true,
        )
        val target = FoundationDBDataStore.open(
            directoryPath = listOf("maryk", "test", "backup-target", Uuid.random().toString()),
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
    fun automaticBackupRejectsProcessLocalSnapshotAcrossWriters() = runTest(timeout = 3.minutes) {
        val directoryPath = listOf("maryk", "test", "backup-multi-writer", Uuid.random().toString())
        val models = mapOf(1u to SimpleMarykModel)
        val firstWriter = FoundationDBDataStore.open(
            directoryPath = directoryPath,
            dataModelsById = models,
            keepAllVersions = true,
        )
        val secondWriter = FoundationDBDataStore.open(
            directoryPath = directoryPath,
            dataModelsById = models,
            keepAllVersions = true,
        )

        try {
            secondWriter.execute(
                SimpleMarykModel.add(SimpleMarykModel.create { value with "written by another process" })
            )

            val exception = assertFailsWith<RequestException> {
                firstWriter.backup(UnusedBackupWriter)
            }
            assertContains(exception.message.orEmpty(), "explicit cluster-authoritative snapshotVersion")
        } finally {
            secondWriter.close()
            firstWriter.close()
        }
    }

    @Test
    fun readContextKeepsOneReadVersionAcrossWrites() = runTest(timeout = 3.minutes) {
        val dataStore = FoundationDBDataStore.open(
            directoryPath = listOf("maryk", "test", "read-context", Uuid.random().toString()),
            dataModelsById = dataModelsForTests,
        )
        try {
            val key = packKey(
                dataStore.getTableDirs(SimpleMarykModel).keysPrefix,
                byteArrayOf(99),
            )
            dataStore.runTransaction { tr -> tr.set(key, byteArrayOf(1)) }

            val readContext = dataStore.createReadContext()
            dataStore.runTransaction { tr -> tr.set(key, byteArrayOf(2)) }

            assertEquals(
                1.toByte(),
                dataStore.runReadTransaction(readContext) { tr ->
                    tr.get(key).awaitResult()?.single()
                },
            )
            assertEquals(
                2.toByte(),
                dataStore.runTransaction { tr ->
                    tr.get(key).awaitResult()?.single()
                },
            )
        } finally {
            dataStore.close()
        }
    }

    @Test
    fun testDataStore() = runTest(timeout = 3.minutes) {
        val dataStore = FoundationDBDataStore.open(
            fdbClusterFilePath = "./fdb.cluster",
            directoryPath = listOf("maryk", "test", "no-history", Uuid.random().toString()),
            dataModelsById = dataModelsForTests,
            keepAllVersions = false,
            databaseOptionsSetter = {
                setTransactionRetryLimit(3)
                setTransactionMaxRetryDelay(5000)
            }
        )

        runDataStoreTests(dataStore)

        dataStore.close()
    }

    @Test
    fun testDataStoreWithKeepAllVersions() = runTest(timeout = 3.minutes) {
        val dataStore = FoundationDBDataStore.open(
            directoryPath = listOf("maryk", "test", "history", Uuid.random().toString()),
            dataModelsById = dataModelsForTests,
            keepAllVersions = true,
        )

        runDataStoreTests(dataStore)

        dataStore.close()
    }

    @Test
    fun testDataStoreWithUpdateHistoryIndex() = runTest(timeout = 3.minutes) {
        val dataStore = FoundationDBDataStore.open(
            directoryPath = listOf("maryk", "test", "update-history", Uuid.random().toString()),
            dataModelsById = dataModelsForTests,
            keepAllVersions = true,
            keepUpdateHistoryIndex = true,
        )

        runDataStoreTests(dataStore)

        dataStore.close()
    }

    @Test
    fun testOrderedScanFlowUpdatesSortedValueWhenPositionStaysSame() = runTest(timeout = 3.minutes) {
        val dataStore = FoundationDBDataStore.open(
            directoryPath = listOf("maryk", "test", "any-value-flow-sorted-value", Uuid.random().toString()),
            dataModelsById = dataModelsForTests,
            keepAllVersions = false,
        )

        try {
            runDataStoreTests(dataStore, "executeOrderedScanFlowUpdatesSortedValueWhenPositionStaysSame")
        } finally {
            dataStore.close()
        }
    }

    @Test
    fun tableScanContinuesAcrossMultipleBatches() = runTest(timeout = 3.minutes) {
        val dataStore = FoundationDBDataStore.open(
            directoryPath = listOf("maryk", "test", "paged-table-scan", Uuid.random().toString()),
            dataModelsById = dataModelsForTests,
            keepAllVersions = false,
        )

        try {
            val values = Array(700) { index ->
                Log(
                    message = "paged-$index",
                    timestamp = LocalDateTime(2024, 1, 1, 0, index / 60, index % 60)
                )
            }
            dataStore.execute(Log.add(*values)).statuses.forEach {
                assertIs<AddSuccess<Log>>(it)
            }

            val response = dataStore.execute(Log.scan(limit = 700u))

            assertEquals(700, response.values.size)
        } finally {
            dataStore.close()
        }
    }
}

private object UnusedBackupWriter : DataStoreBackupWriter {
    override suspend fun begin(manifest: DataStoreBackupManifest) = Unit
    override suspend fun write(chunk: DataStoreBackupChunk) = Unit
    override suspend fun complete() = Unit
}
