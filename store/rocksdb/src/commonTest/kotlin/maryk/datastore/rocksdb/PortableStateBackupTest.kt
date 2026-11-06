package maryk.datastore.rocksdb

import maryk.datastore.test.assertDeletedStateAllocations

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import maryk.core.exceptions.RequestException
import maryk.core.query.changes.Change
import maryk.core.query.changes.change
import maryk.core.query.pairs.with
import maryk.core.query.requests.add
import maryk.core.query.requests.change
import maryk.core.query.requests.delete
import maryk.core.query.requests.scan
import maryk.createTestDBFolder
import maryk.datastore.shared.DataStoreStateBackupManifest
import maryk.datastore.shared.DataStoreStateBackupChunk
import maryk.datastore.shared.DataStoreStateBackupOptions
import maryk.datastore.shared.DataStoreStateBackupWriter
import maryk.datastore.shared.backupState
import maryk.datastore.shared.restoreState
import maryk.datastore.test.stateBackupTestEncryptionProvider
import maryk.datastore.test.stateBackupModels
import maryk.datastore.test.advancedStateBackupModels
import maryk.datastore.test.assertStateCaptureClosesCleanly
import maryk.datastore.test.assertAdvancedStateRoundTrip
import maryk.datastore.test.assertAdvancedStateMatches
import maryk.datastore.test.assertStateBatchRollback
import maryk.datastore.test.SerializedStateBackup
import maryk.deleteFolder
import maryk.test.models.SimpleMarykModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith

class PortableStateBackupTest {
    @Test
    fun allModelsMustBeReadyBeforePinningTheCapture() = runTest {
        val path = createTestDBFolder("state-pending-migration")
        val source = RocksDBDataStore.open(relativePath = path, dataModelsById = stateBackupModels, keepAllVersions = false)
        val archive = SerializedStateBackup(source)
        var began = false
        try {
            // Model 2 is not the request's anchor model. A destination may suspend long enough
            // for its background migration to finish after the read view was already pinned.
            source.pendingMigrationModelIds.value = setOf(2u)
            assertFailsWith<RequestException> {
                source.backupState(object : DataStoreStateBackupWriter by archive {
                    override suspend fun begin(manifest: DataStoreStateBackupManifest) {
                        began = true
                        source.pendingMigrationModelIds.value = emptySet()
                        archive.begin(manifest)
                    }
                })
            }
            assertFalse(began)
            assertFalse(archive.completed)
        } finally {
            source.pendingMigrationModelIds.value = emptySet()
            source.close()
            deleteFolder(path)
        }
    }

    @Test
    fun closeCancelsSuspendedStateCaptureAndReleasesNativeHandles() = runTest {
        for (models in listOf(mapOf(1u to SimpleMarykModel), emptyMap())) {
            val path = createTestDBFolder("state-close-source-${models.size}")
            val source = RocksDBDataStore.open(relativePath = path, dataModelsById = models, keepAllVersions = false)
            try {
                assertStateCaptureClosesCleanly(source)
                val reopened = RocksDBDataStore.open(relativePath = path, dataModelsById = models, keepAllVersions = false)
                reopened.close()
            } finally { source.close(); deleteFolder(path) }
        }
    }

    @Test
    fun complexArchiveAndRollbackSurviveReopening() = runTest {
        val sourcePath = createTestDBFolder("state-complex-source")
        val targetPath = createTestDBFolder("state-complex-target")
        val source = RocksDBDataStore.open(relativePath = sourcePath, dataModelsById = advancedStateBackupModels, keepAllVersions = true, fieldEncryptionProvider = stateBackupTestEncryptionProvider(1))
        var target = RocksDBDataStore.open(relativePath = targetPath, dataModelsById = advancedStateBackupModels, keepAllVersions = false, fieldEncryptionProvider = stateBackupTestEncryptionProvider(2))
        try {
            assertStateBatchRollback(target)
            val expected = assertAdvancedStateRoundTrip(source, target)
            target.close()
            target = RocksDBDataStore.open(relativePath = targetPath, dataModelsById = advancedStateBackupModels, keepAllVersions = false, fieldEncryptionProvider = stateBackupTestEncryptionProvider(2))
            assertAdvancedStateMatches(target, expected)
            assertDeletedStateAllocations(target)
            target.verifyStorageChecksums()
        } finally { source.close(); target.close(); deleteFolder(sourcePath); deleteFolder(targetPath) }
    }

    @Test
    fun pinnedSnapshotSurvivesConcurrentUpdatesAndHardDeletesAcrossPages() = runTest {
        val sourcePath = createTestDBFolder("state-snapshot-source")
        val targetPath = createTestDBFolder("state-snapshot-target")
        val models = mapOf(1u to SimpleMarykModel)
        val source = RocksDBDataStore.open(relativePath = sourcePath, dataModelsById = models, keepAllVersions = false)
        val target = RocksDBDataStore.open(relativePath = targetPath, dataModelsById = models, keepAllVersions = false)
        val captured = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        try {
            source.execute(SimpleMarykModel.add(
                SimpleMarykModel.create { value with "ha snapshot one" },
                SimpleMarykModel.create { value with "ha snapshot two" },
                SimpleMarykModel.create { value with "ha snapshot three" },
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
            // These writes complete while the export is paused; later pages must use its old view.
            source.execute(SimpleMarykModel.delete(expected.last().key, hardDelete = true))
            source.execute(SimpleMarykModel.change(expected[1].key.change(Change(SimpleMarykModel.ref { value } with "ha changed during export"))))
            source.execute(SimpleMarykModel.add(SimpleMarykModel.create { value with "ha added during export" }))
            release.complete(Unit)
            backup.await()
            target.restoreState(archive)
            assertEquals(expected, target.execute(SimpleMarykModel.scan(allowTableScan = true)).values)
            target.verifyStorageChecksums()
        } finally {
            release.complete(Unit)
            source.close()
            target.close()
            deleteFolder(sourcePath)
            deleteFolder(targetPath)
        }
    }
}
