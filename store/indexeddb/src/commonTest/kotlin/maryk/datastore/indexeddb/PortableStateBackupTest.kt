package maryk.datastore.indexeddb

import maryk.datastore.test.assertDeletedStateAllocations

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
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
import maryk.datastore.shared.restoreState
import maryk.datastore.test.assertStateCaptureClosesCleanly
import maryk.datastore.test.advancedStateBackupModels
import maryk.datastore.test.assertAdvancedStateMatches
import maryk.datastore.test.assertAdvancedStateRoundTrip
import maryk.datastore.test.assertStateBatchRollback
import maryk.datastore.test.stateBackupTestEncryptionProvider
import maryk.datastore.test.SerializedStateBackup
import maryk.test.models.SimpleMarykModel
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.time.Duration.Companion.minutes

class PortableStateBackupTest {
    @Test
    fun closeCancelsCapturesWithAndWithoutRegisteredModels() = runTest(timeout = 3.minutes) {
        installIndexedDbForTests()
        for (models in listOf(mapOf(1u to SimpleMarykModel), emptyMap())) {
            val source = IndexedDbDataStore.open(databaseName = "maryk-state-close-${Random.nextInt()}", dataModelsById = models)
            try { assertStateCaptureClosesCleanly(source) }
            finally { source.close() }
        }
    }

    @Test
    fun complexArchiveAndRollbackSurviveReopening() = runTest(timeout = 3.minutes) {
        installIndexedDbForTests()
        val name = "maryk-state-complex-${Random.nextInt()}"
        val source = IndexedDbDataStore.open(databaseName = "$name-source", dataModelsById = advancedStateBackupModels,
            keepAllVersions = true, fieldEncryptionProvider = stateBackupTestEncryptionProvider(1))
        var target = IndexedDbDataStore.open(databaseName = "$name-target", dataModelsById = advancedStateBackupModels,
            keepAllVersions = false, fieldEncryptionProvider = stateBackupTestEncryptionProvider(2))
        try {
            assertStateBatchRollback(target)
            val expected = assertAdvancedStateRoundTrip(source, target)
            target.close()
            target = IndexedDbDataStore.open(databaseName = "$name-target", dataModelsById = advancedStateBackupModels,
                keepAllVersions = false, fieldEncryptionProvider = stateBackupTestEncryptionProvider(2))
            assertAdvancedStateMatches(target, expected)
            assertDeletedStateAllocations(target)
        } finally { source.close(); target.close() }
    }

    @Test
    fun captureFencesWritersFromAnotherStoreContext() = runTest(timeout = 3.minutes) {
        installIndexedDbForTests()
        val models = mapOf(1u to SimpleMarykModel)
        val databaseName = "maryk-state-fence-${Random.nextInt()}"
        val source = IndexedDbDataStore.open(databaseName = databaseName, dataModelsById = models)
        val otherWriter = IndexedDbDataStore.open(databaseName = databaseName, dataModelsById = models)
        val target = IndexedDbDataStore.open(databaseName = "$databaseName-target", dataModelsById = models)
        val captured = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        try {
            source.execute(SimpleMarykModel.add(
                SimpleMarykModel.create { value with "ha fenced one" },
                SimpleMarykModel.create { value with "ha fenced two" },
            ))
            val expected = source.execute(SimpleMarykModel.scan(allowTableScan = true)).values
            val archive = SerializedStateBackup(source)
            val backup = async {
                source.backupState(object : DataStoreStateBackupWriter by archive {
                    override suspend fun write(chunk: DataStoreStateBackupChunk) {
                        archive.write(chunk)
                        captured.complete(Unit)
                        release.await()
                    }
                }, DataStoreStateBackupOptions(batchSize = 1u))
            }
            captured.await()
            val write = async { otherWriter.execute(SimpleMarykModel.add(SimpleMarykModel.create { value with "ha after fence" })) }
            withContext(Dispatchers.Default) { delay(100) }
            assertFalse(write.isCompleted)
            release.complete(Unit)
            backup.await()
            write.await()
            target.restoreState(archive)
            assertEquals(expected, target.execute(SimpleMarykModel.scan(allowTableScan = true)).values)
        } finally {
            release.complete(Unit)
            source.close()
            otherWriter.close()
            target.close()
        }
    }
}
