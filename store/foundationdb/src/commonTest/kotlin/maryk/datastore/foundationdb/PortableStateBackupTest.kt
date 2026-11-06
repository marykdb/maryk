package maryk.datastore.foundationdb

import maryk.datastore.test.assertDeletedStateAllocations

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import maryk.core.query.pairs.with
import maryk.core.query.requests.add
import maryk.core.query.requests.scan
import maryk.datastore.shared.DataStoreStateBackupChunk
import maryk.datastore.shared.DataStoreStateBackupOptions
import maryk.datastore.shared.DataStoreStateBackupWriter
import maryk.datastore.shared.backupState
import maryk.datastore.test.assertStateCaptureClosesCleanly
import maryk.datastore.test.advancedStateBackupModels
import maryk.datastore.test.assertAdvancedStateMatches
import maryk.datastore.test.assertAdvancedStateRoundTrip
import maryk.datastore.test.assertStateBatchRollback
import maryk.datastore.test.stateBackupTestEncryptionProvider
import maryk.datastore.test.SerializedStateBackup
import maryk.test.models.SimpleMarykModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.time.Duration.Companion.minutes
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class PortableStateBackupTest {
    @Test
    fun closeCancelsCapturesWithAndWithoutRegisteredModels() = runTest(timeout = 3.minutes) {
        for (models in listOf(mapOf(1u to SimpleMarykModel), emptyMap())) {
            val source = FoundationDBDataStore.open(directoryPath = listOf("maryk", "test", "state-close", Uuid.random().toString()),
                dataModelsById = models, keepAllVersions = false)
            try { assertStateCaptureClosesCleanly(source) }
            finally { source.close() }
        }
    }

    @Test
    fun complexArchiveAndRollbackSurviveReopening() = runTest(timeout = 3.minutes) {
        val prefix = listOf("maryk", "test", "state-complex", Uuid.random().toString())
        val source = FoundationDBDataStore.open(directoryPath = prefix + "source", dataModelsById = advancedStateBackupModels,
            keepAllVersions = true, fieldEncryptionProvider = stateBackupTestEncryptionProvider(1))
        var target = FoundationDBDataStore.open(directoryPath = prefix + "target", dataModelsById = advancedStateBackupModels,
            keepAllVersions = false, fieldEncryptionProvider = stateBackupTestEncryptionProvider(2))
        try {
            assertStateBatchRollback(target)
            val expected = assertAdvancedStateRoundTrip(source, target)
            target.close()
            target = FoundationDBDataStore.open(directoryPath = prefix + "target", dataModelsById = advancedStateBackupModels,
                keepAllVersions = false, fieldEncryptionProvider = stateBackupTestEncryptionProvider(2))
            assertAdvancedStateMatches(target, expected)
            assertDeletedStateAllocations(target)
        } finally { source.close(); target.close() }
    }

    @Test
    fun expiredCaptureNeverCompletesAndLeavesStoreUsable() = runTest(timeout = 3.minutes) {
        val source = FoundationDBDataStore.open(
            directoryPath = listOf("maryk", "test", "state-expiry", Uuid.random().toString()),
            dataModelsById = mapOf(1u to SimpleMarykModel),
            keepAllVersions = false,
        )
        try {
            source.execute(SimpleMarykModel.add(
                SimpleMarykModel.create { value with "ha expiry one" },
                SimpleMarykModel.create { value with "ha expiry two" },
            ))
            val archive = SerializedStateBackup(source)
            assertFailsWith<FoundationDBSnapshotExpiredException> {
                source.backupState(object : DataStoreStateBackupWriter by archive {
                    override suspend fun write(chunk: DataStoreStateBackupChunk) {
                        archive.write(chunk)
                        // The backend explicitly expires pinned contexts after four real seconds.
                        withContext(Dispatchers.Default) { delay(4200) }
                    }
                }, DataStoreStateBackupOptions(batchSize = 1u))
            }
            assertFalse(archive.completed)
            assertEquals(2, source.execute(SimpleMarykModel.scan(allowTableScan = true)).values.size)
        } finally { source.close() }
    }
}
