package maryk.datastore.memory

import maryk.datastore.test.assertDeletedStateAllocations

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import maryk.core.exceptions.RequestException
import maryk.core.models.IsRootDataModel
import maryk.core.query.ValuesWithMetaData
import maryk.core.query.pairs.with
import maryk.core.query.requests.add
import maryk.core.query.requests.scan
import maryk.datastore.shared.DataStoreStateBackupChunk
import maryk.datastore.shared.DataStoreStateBackupManifest
import maryk.datastore.shared.DataStoreStateBackupOptions
import maryk.datastore.shared.DataStoreStateBackupReader
import maryk.datastore.shared.DataStoreStateBackupWriter
import maryk.datastore.shared.IsDataStore
import maryk.datastore.shared.backupState
import maryk.datastore.shared.restoreState
import maryk.datastore.test.advancedStateBackupModels
import maryk.datastore.test.assertStateCaptureClosesCleanly
import maryk.datastore.test.assertAdvancedStateRoundTrip
import maryk.datastore.test.assertStateBatchRollback
import maryk.datastore.test.stateBackupModels
import maryk.datastore.test.DataStoreStateBackupTest
import maryk.datastore.test.UniqueModel
import maryk.datastore.test.SerializedStateBackup
import maryk.test.models.SimpleMarykModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class MemoryStateBackupTest {
    private val models = mapOf(1u to SimpleMarykModel)
    private fun store(history: Boolean = false) = InMemoryDataStore.open(dataModelsById = models, keepAllVersions = history)

    @Test
    fun emptyRegistryCaptureUsesTheSameCloseLifecycle() = runTest {
        val source = InMemoryDataStore.open(dataModelsById = emptyMap())
        val entered = CompletableDeferred<Unit>()
        val capture = async {
            runCatching {
                source.backupState(object : DataStoreStateBackupWriter {
                    override suspend fun begin(manifest: DataStoreStateBackupManifest) { entered.complete(Unit); awaitCancellation() }
                    override suspend fun write(chunk: DataStoreStateBackupChunk) = Unit
                    override suspend fun complete() = error("Cancelled capture was published")
                })
            }
        }
        try {
            entered.await()
            source.close()
            runCurrent()
            assertTrue(capture.isCompleted, "Closing an empty store must cancel its capture too")
            assertTrue(capture.await().isFailure)
        } finally { capture.cancelAndJoin(); source.close() }
    }

    @Test
    fun closeCancelsSuspendedStateCapture() = runTest {
        val source = store()
        try { assertStateCaptureClosesCleanly(source) }
        finally { source.close() }
    }

    @Test
    fun complexArchiveAndAtomicRollback() = runTest {
        val source = InMemoryDataStore.open(dataModelsById = advancedStateBackupModels)
        val target = InMemoryDataStore.open(dataModelsById = advancedStateBackupModels)
        try {
            assertStateBatchRollback(target)
            assertAdvancedStateRoundTrip(source, target)
            assertDeletedStateAllocations(target)
        } finally { source.close(); target.close() }
    }

    @Test
    fun roundTripWithAndWithoutSourceHistory() = runTest {
        for (history in listOf(false, true)) {
            val source = InMemoryDataStore.open(dataModelsById = stateBackupModels, keepAllVersions = history)
            val target = InMemoryDataStore.open(dataModelsById = stateBackupModels)
            try { DataStoreStateBackupTest(source, target).restoresStateWithoutHistory() }
            finally { source.close(); target.close() }
        }
    }

    @Test
    fun memoryWritersWaitUntilEveryPageHasBeenCaptured() = runTest {
        val source = store()
        val firstChunk = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        try {
            source.execute(SimpleMarykModel.add(SimpleMarykModel.create { value with "ha before" }))
            val archive = SerializedStateBackup(source)
            val backup = async {
                source.backupState(object : DataStoreStateBackupWriter by archive {
                    override suspend fun write(chunk: DataStoreStateBackupChunk) {
                        archive.write(chunk)
                        firstChunk.complete(Unit)
                        release.await()
                    }
                }, DataStoreStateBackupOptions(batchSize = 1u))
            }
            firstChunk.await()
            val write = async { source.execute(SimpleMarykModel.add(SimpleMarykModel.create { value with "ha after" })) }
            runCurrent()
            assertFalse(write.isCompleted)
            release.complete(Unit)
            backup.await()
            write.await()
            val target = store()
            try { assertEquals(1uL, target.restoreState(archive).records) }
            finally { target.close() }
        } finally { release.complete(Unit); source.close() }
    }

    @Test
    fun failedAndCancelledWritersReleaseTheStoreWithoutCompleting() = runTest {
        val source = store()
        try {
            source.execute(SimpleMarykModel.add(SimpleMarykModel.create { value with "ha before failure" }))
            val archive = SerializedStateBackup(source)
            assertFailsWith<IllegalStateException> {
                source.backupState(object : DataStoreStateBackupWriter by archive {
                    override suspend fun write(chunk: DataStoreStateBackupChunk) { error("writer failed") }
                })
            }
            assertFalse(archive.completed)
            val entered = CompletableDeferred<Unit>()
            val backup = launch {
                source.backupState(object : DataStoreStateBackupWriter by archive {
                    override suspend fun write(chunk: DataStoreStateBackupChunk) {
                        entered.complete(Unit)
                        awaitCancellation()
                    }
                })
            }
            entered.await()
            backup.cancelAndJoin()
            assertFalse(archive.completed)
            source.execute(SimpleMarykModel.add(SimpleMarykModel.create { value with "ha after failure" }))
            assertEquals(2, source.execute(SimpleMarykModel.scan(allowTableScan = true)).values.size)
        } finally { source.close() }
    }

    @Test
    fun duplicateAndInvalidRecordsAreRejectedBeforeTheirChunkIsApplied() = runTest {
        val source = store()
        try {
            source.execute(SimpleMarykModel.add(SimpleMarykModel.create { value with "ha validation" }))
            val archive = SerializedStateBackup(source)
            source.backupState(archive)
            val records = mutableListOf<ValuesWithMetaData<IsRootDataModel>>()
            archive.read { records += it.records }
            val record = records.single()
            for (invalid in listOf(listOf(record, record), listOf(record.copy(firstVersion = record.lastVersion + 1uL)))) {
                val target = store()
                try {
                    assertFailsWith<RequestException> {
                        target.restoreState(object : DataStoreStateBackupReader {
                            override val manifest = archive.manifest
                            override suspend fun read(consumer: suspend (DataStoreStateBackupChunk) -> Unit) {
                                consumer(DataStoreStateBackupChunk(SimpleMarykModel.Meta.name, invalid))
                            }
                        })
                    }
                    assertTrue(target.execute(SimpleMarykModel.scan(allowTableScan = true)).values.isEmpty())
                } finally { target.close() }
            }
        } finally { source.close() }
    }

    @Test
    fun historyAndNonemptyTargetsAreRejectedAndLargeRecordsStayUnpublished() = runTest {
        val source = store()
        val historyTarget = store(true)
        val nonemptyTarget = store()
        try {
            val values = SimpleMarykModel.create { value with "ha ${"x".repeat(1000)}" }
            source.execute(SimpleMarykModel.add(values))
            nonemptyTarget.execute(SimpleMarykModel.add(values))
            val archive = SerializedStateBackup(source)
            assertFailsWith<RequestException> { source.backupState(archive, DataStoreStateBackupOptions(maxChunkBytes = 100)) }
            assertFalse(archive.completed)
            source.backupState(archive)
            assertFailsWith<RequestException> { historyTarget.restoreState(archive) }
            assertFailsWith<RequestException> { nonemptyTarget.restoreState(archive) }
        } finally { source.close(); historyTarget.close(); nonemptyTarget.close() }
    }
    @Test
    fun duplicateUniqueValuesRollBackTheEntireStateBatchAcrossChangeVersions() = runTest {
        val uniqueModels = mapOf(1u to UniqueModel)
        val source = InMemoryDataStore.open(dataModelsById = uniqueModels)
        val target = InMemoryDataStore.open(dataModelsById = uniqueModels)
        try {
            source.execute(UniqueModel.add(UniqueModel.create { email with "first@example.com" }))
            source.execute(UniqueModel.add(UniqueModel.create { email with "second@example.com" }))
            val archive = SerializedStateBackup(source)
            source.backupState(archive)
            val records = mutableListOf<ValuesWithMetaData<IsRootDataModel>>()
            archive.read { records += it.records }
            assertTrue(records[0].lastVersion != records[1].lastVersion)
            val duplicate = listOf(records[0], records[1].copy(values = records[0].values))
            assertFailsWith<RequestException> {
                target.restoreState(object : DataStoreStateBackupReader {
                    override val manifest = archive.manifest
                    override suspend fun read(consumer: suspend (DataStoreStateBackupChunk) -> Unit) {
                        consumer(DataStoreStateBackupChunk(UniqueModel.Meta.name, duplicate))
                    }
                })
            }
            assertTrue(target.execute(UniqueModel.scan(allowTableScan = true)).values.isEmpty())
        } finally { source.close(); target.close() }
    }

    @Test
    fun adaptersAndInvalidManifestsFailBeforeReadingOrChangingTargets() = runTest {
        val source = store()
        val target = store()
        try {
            val archive = SerializedStateBackup(source)
            val adapter = object : IsDataStore by source {}
            assertFailsWith<RequestException> { adapter.backupState(archive) }
            source.backupState(archive)
            val name = SimpleMarykModel.Meta.name
            for (manifest in listOf(
                archive.manifest.copy(formatVersion = 99u),
                archive.manifest.copy(modelNames = listOf(name, name)),
                archive.manifest.copy(modelMajorVersions = emptyMap()),
                archive.manifest.copy(modelMajorVersions = mapOf(name to (SimpleMarykModel.Meta.version.major + 1u).toUShort())),
            )) {
                assertFailsWith<RequestException> {
                    target.restoreState(object : DataStoreStateBackupReader {
                        override val manifest = manifest
                        override suspend fun read(consumer: suspend (DataStoreStateBackupChunk) -> Unit) { error("Invalid manifest was read") }
                    })
                }
            }
            assertTrue(target.execute(SimpleMarykModel.scan(allowTableScan = true)).values.isEmpty())
        } finally { source.close(); target.close() }
    }

}
