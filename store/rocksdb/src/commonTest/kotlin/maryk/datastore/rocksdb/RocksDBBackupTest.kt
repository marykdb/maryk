package maryk.datastore.rocksdb

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import maryk.core.models.RootDataModel
import maryk.core.properties.definitions.string
import maryk.datastore.shared.encryption.AesGcmHmacSha256EncryptionProvider
import maryk.core.query.responses.statuses.AddSuccess
import maryk.core.models.key
import maryk.core.query.changes.Change
import maryk.core.query.changes.change
import maryk.core.query.pairs.with
import maryk.core.query.requests.AtomicRequest
import maryk.datastore.shared.executeAtomic
import maryk.core.query.requests.add
import maryk.core.query.requests.change
import maryk.core.query.requests.delete
import maryk.core.query.requests.get
import maryk.createTestDBFolder
import maryk.datastore.rocksdb.metadata.readStoreMetaFile
import maryk.datastore.rocksdb.model.modelHlcWatermarkKey
import maryk.deleteFolder
import maryk.file.File
import maryk.rocksdb.FlushOptions
import maryk.rocksdb.BackupEngineOptions
import maryk.rocksdb.getDefaultEnv
import maryk.rocksdb.openBackupEngine
import maryk.rocksdb.RocksDBException
import maryk.rocksdb.WriteBatch
import maryk.rocksdb.WriteOptions
import maryk.test.models.Log
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

class RocksDBBackupTest {
    @Test
    fun restoresCurrentStateHistoryAndMarykMetadataWithoutSource() = runTest(timeout = 3.minutes) {
        for (history in listOf(false, true)) {
            val sourcePath = createTestDBFolder("physical-source-$history")
            val repositoryPath = createTestDBFolder("physical-backups-$history")
            val destination = "$repositoryPath-restored"
            val source = RocksDBDataStore.open(relativePath = sourcePath, dataModelsById = mapOf(1u to Log),
                keepAllVersions = history, keepUpdateHistoryIndex = history)
            var repository = RocksDBBackupRepository.open(repositoryPath)
            try {
                val value = Log("before")
                val key = Log.key(value)
                source.execute(Log.add(value))
                val snapshot = if (history) source.captureSnapshotVersion() else null
                val atomicChange = AtomicRequest("physical-backup-change", Log.change(key.change(Change(Log.ref { message } with "after"))))
                val receipt = source.executeAtomic(atomicChange)
                val expectedMeta = readStoreMetaFile(sourcePath)
                val expectedWatermark = source.db.get(source.getColumnFamilies(1u).model, modelHlcWatermarkKey)
                val backup = repository.createBackup(source)
                assertEquals(history, backup.keepAllVersions)
                assertEquals(history, backup.keepUpdateHistoryIndex)
                assertTrue(backup.sizeBytes > 0)
                source.execute(Log.delete(key, hardDelete = true))
                source.close()
                deleteFolder(sourcePath)
                repository.close()
                repository = RocksDBBackupRepository.open(repositoryPath)
                assertEquals(listOf(backup), repository.backups())
                repository.restoreBackup(backup.id, destination)
                assertEquals(expectedMeta, readStoreMetaFile(destination))
                val restored = RocksDBDataStore.open(relativePath = destination, dataModelsById = mapOf(1u to Log),
                    keepAllVersions = backup.keepAllVersions, keepUpdateHistoryIndex = backup.keepUpdateHistoryIndex)
                try {
                    assertEquals("after", restored.execute(Log.get(key)).values.single().values { message })
                    assertEquals(receipt, restored.executeAtomic(atomicChange), "Physical recovery must retain the retry receipt")
                    if (snapshot != null) assertEquals("before", restored.execute(Log.get(key, toVersion = snapshot)).values.single().values { message })
                    assertContentEquals(expectedWatermark, restored.db.get(restored.getColumnFamilies(1u).model, modelHlcWatermarkKey))
                    restored.verifyStorageChecksums()
                } finally { restored.close() }
            } finally {
                source.close()
                repository.close()
                deleteFolder(sourcePath)
                deleteFolder(repositoryPath)
                deleteFolder(destination)
            }
        }
    }

    @Test
    fun capturesWalAndFlushesWalDisabledWrites() = runTest {
        val sourcePath = createTestDBFolder("physical-wal-source")
        val repositoryPath = createTestDBFolder("physical-wal-backups")
        val source = RocksDBDataStore.open(relativePath = sourcePath, dataModelsById = mapOf(1u to Log))
        val repository = RocksDBBackupRepository.open(repositoryPath)
        try {
            val family = source.getColumnFamilies(1u).model
            val key = byteArrayOf(91, 1)
            WriteOptions().setDisableWAL(true).use { source.db.put(family, it, key, byteArrayOf(42)) }
            val flushed = repository.createBackup(source)
            source.db.put(family, key, byteArrayOf(43))
            val wal = repository.createBackup(source, flushBeforeBackup = false)
            for ((backup, expected) in listOf(flushed to 42, wal to 43)) {
                val destination = "$repositoryPath-restore-${backup.id}"
                try {
                    repository.restoreBackup(backup.id, destination)
                    val restored = RocksDBDataStore.open(relativePath = destination, dataModelsById = mapOf(1u to Log))
                    try {
                        assertContentEquals(byteArrayOf(expected.toByte()), restored.db.get(restored.getColumnFamilies(1u).model, key))
                        restored.verifyStorageChecksums()
                    } finally { restored.close() }
                } finally { deleteFolder(destination) }
            }
        } finally {
            source.close()
            repository.close()
            deleteFolder(sourcePath)
            deleteFolder(repositoryPath)
        }
    }

    @Test
    fun concurrentAtomicWritesAndIncrementalRetentionRemainRestorable() = runTest(timeout = 3.minutes) {
        val sourcePath = createTestDBFolder("physical-concurrent-source")
        val repositoryPath = createTestDBFolder("physical-incremental-backups")
        val destination = "$repositoryPath-restored"
        val source = RocksDBDataStore.open(relativePath = sourcePath, dataModelsById = mapOf(1u to Log))
        val repository = RocksDBBackupRepository.open(repositoryPath)
        try {
            val family = source.getColumnFamilies(1u).model
            val key1 = byteArrayOf(92, 1)
            val key2 = byteArrayOf(92, 2)
            val writer = async(Dispatchers.Default) {
                WriteOptions().use { options ->
                    repeat(200) { version ->
                        WriteBatch().use { batch ->
                            batch.put(family, key1, byteArrayOf(version.toByte()))
                            batch.put(key2, byteArrayOf(version.toByte()))
                            source.db.write(options, batch)
                        }
                    }
                }
            }
            val first = try { repository.createBackup(source, flushBeforeBackup = false) } finally { writer.await() }
            // Check the backup captured during writes before retention removes it. Both column
            // families must reflect the same atomic batch, even if no batch had committed yet.
            repository.restoreBackup(first.id, destination)
            val concurrentRestore = RocksDBDataStore.open(relativePath = destination, dataModelsById = mapOf(1u to Log))
            try {
                assertContentEquals(concurrentRestore.db.get(concurrentRestore.getColumnFamilies(1u).model, key1),
                    concurrentRestore.db.get(key2))
                concurrentRestore.verifyStorageChecksums()
            } finally { concurrentRestore.close() }
            deleteFolder(destination)
            FlushOptions().setWaitForFlush(true).use { source.db.flush(it, source.storageUpgradeHandles) }
            val second = repository.createBackup(source)
            val third = repository.createBackup(source)
            assertEquals(listOf(first.id, second.id, third.id), repository.backups().map { it.id })
            repository.purgeOldBackups(2)
            assertEquals(listOf(second.id, third.id), repository.backups().map { it.id })
            repository.deleteBackup(second.id)
            repository.restoreBackup(third.id, destination)
            val restored = RocksDBDataStore.open(relativePath = destination, dataModelsById = mapOf(1u to Log))
            try {
                val targetFamily = restored.getColumnFamilies(1u).model
                assertContentEquals(byteArrayOf(199.toByte()), restored.db.get(targetFamily, key1))
                assertContentEquals(restored.db.get(targetFamily, key1), restored.db.get(key2))
                restored.verifyStorageChecksums()
            } finally { restored.close() }
            assertFalse(repository.corruptedBackupIds().isNotEmpty())
        } finally {
            source.close()
            repository.close()
            deleteFolder(sourcePath)
            deleteFolder(repositoryPath)
            deleteFolder(destination)
        }
    }

    @Test
    fun rejectsUnsafeDestinationsPendingMigrationsAndClosedOwners() = runTest {
        val sourcePath = createTestDBFolder("physical-safety-source")
        val repositoryPath = createTestDBFolder("physical-safety-backups")
        val source = RocksDBDataStore.open(relativePath = sourcePath, dataModelsById = mapOf(1u to Log))
        val repository = RocksDBBackupRepository.open(repositoryPath)
        try {
            val backup = repository.createBackup(source)
            assertFailsWith<IllegalArgumentException> { repository.restoreBackup(backup.id, sourcePath) }
            assertFailsWith<IllegalArgumentException> { repository.restoreBackup(backup.id, "$repositoryPath/nested") }
            assertFailsWith<IllegalArgumentException> { repository.restoreBackup(backup.id, "$repositoryPath/../${repositoryPath.substringAfterLast('/')}") }
            assertFailsWith<IllegalArgumentException> { repository.restoreBackup(Int.MAX_VALUE, "$repositoryPath-missing") }
            assertFailsWith<IllegalArgumentException> { repository.purgeOldBackups(0) }
            assertTrue(source.execute(Log.get(Log.key(Log("absent")))).values.isEmpty())
            source.pendingMigrationModelIds.value = setOf(1u)
            assertFailsWith<IllegalStateException> { repository.createBackup(source) }
            source.pendingMigrationModelIds.value = emptySet()
            source.close()
            assertFailsWith<IllegalStateException> { repository.createBackup(source) }
            repository.close()
            assertFailsWith<IllegalStateException> { repository.backups() }
        } finally {
            source.pendingMigrationModelIds.value = emptySet()
            source.close()
            repository.close()
            deleteFolder(sourcePath)
            deleteFolder(repositoryPath)
        }
    }

    @Test
    fun closeWaitsForBackupAndDirectResourceCloseFailsDuringBackup() = runTest {
        val sourcePath = createTestDBFolder("physical-close-source")
        val source = RocksDBDataStore.open(relativePath = sourcePath, dataModelsById = mapOf(1u to Log))
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        try {
            val backup = async(Dispatchers.Default) {
                source.withPhysicalBackup { _, _, _ ->
                    entered.complete(Unit)
                    runBlocking { release.await() }
                }
            }
            entered.await()
            assertFailsWith<IllegalStateException> { source.closeResources() }
            val closing = async { source.close() }
            assertFalse(closing.isCompleted)
            release.complete(Unit)
            backup.await()
            closing.await()
            assertFailsWith<IllegalStateException> { source.withPhysicalBackup { _, _, _ -> } }
        } finally {
            release.complete(Unit)
            source.close()
            deleteFolder(sourcePath)
        }
    }

    @Test
    fun restoreRejectsCorruptedSstAndUnknownBackupMetadata() = runTest {
        val sourcePath = createTestDBFolder("physical-corrupt-source")
        val repositoryPath = createTestDBFolder("physical-corrupt-backups")
        val destination = "$repositoryPath-restored"
        val source = RocksDBDataStore.open(relativePath = sourcePath, dataModelsById = mapOf(1u to Log))
        var repository = RocksDBBackupRepository.open(repositoryPath)
        try {
            source.execute(Log.add(Log("preserve checksum ".repeat(100))))
            val backup = repository.createBackup(source)
            repository.close()
            val manifest = assertNotNull(File.readText("$repositoryPath/meta/${backup.id}"))
            val sst = assertNotNull(Regex("shared_checksum/[^\\s]+").find(manifest)).value
            val bytes = assertNotNull(File.readBytes("$repositoryPath/$sst"))
            bytes[0] = (bytes[0].toInt() xor 1).toByte()
            File.writeBytes("$repositoryPath/$sst", bytes)
            repository = RocksDBBackupRepository.open(repositoryPath)
            assertFailsWith<RocksDBException> { repository.restoreBackup(backup.id, destination) }
            assertTrue(backupPathExists("$destination/$INCOMPLETE_RESTORE_DIRECTORY"))
            assertFailsWith<IllegalStateException> {
                RocksDBDataStore.open(relativePath = destination, dataModelsById = mapOf(1u to Log))
            }
            assertFailsWith<IllegalArgumentException> { decodeBackupMetadata(null) }
            assertFailsWith<IllegalArgumentException> { decodeBackupMetadata("maryk-rocksdb-backup:2\ntrue\ntrue\n{}") }
        } finally {
            source.close()
            repository.close()
            deleteFolder(sourcePath)
            deleteFolder(repositoryPath)
            deleteFolder(destination)
        }
    }
    @Test
    fun exclusiveOwnershipAndCanonicalPathsSurviveCloseAndReopen() = runTest {
        val working = canonicalBackupDirectory(".")
        val windows = working.getOrNull(1) == ':' || working.startsWith("//")
        val path = createTestDBFolder(if (windows) "physical-owner-İ" else "physical-owner-İ-\\literal")
        val canonical = canonicalBackupDirectory(path)
        assertEquals(canonical, canonicalBackupDirectory(canonical))
        val first = RocksDBBackupRepository.open(path)
        try {
            assertFailsWith<IllegalStateException> { RocksDBBackupRepository.open("$canonical/.") }
        } finally { first.close() }
        val second = RocksDBBackupRepository.open(path)
        try { assertTrue(second.backups().isEmpty()) } finally { second.close() }
        try {
            File.writeText("$path/plain-file", "not a directory")
            assertFailsWith<IllegalArgumentException> { canonicalBackupDirectory("$path/plain-file") }
            assertFailsWith<IllegalArgumentException> { requireSeparateBackupDirectories("C:/Data", "c:/DATA/nested") }
        } finally { deleteFolder(path) }
    }

    @Test
    fun unreadableMetadataDoesNotBlockHealthyRecoveryOrDisplaceRetention() = runTest {
        val sourcePath = createTestDBFolder("physical-readable-source")
        val path = createTestDBFolder("physical-readable-backups")
        val destination = "$path-restored"
        val source = RocksDBDataStore.open(relativePath = sourcePath, dataModelsById = mapOf(1u to Log))
        var repository = RocksDBBackupRepository.open(path)
        try {
            val value = Log("healthy recovery point")
            source.execute(Log.add(value))
            val good = repository.createBackup(source)
            repository.close()
            // A newer producer's unsupported metadata must not prevent restoring this older backup.
            val options = BackupEngineOptions(canonicalBackupDirectory(path))
            try {
                val raw = openBackupEngine(getDefaultEnv(), options)
                try {
                    raw.createNewBackupWithMetadata(source.db, "maryk-rocksdb-backup:3\ntrue\nfalse\n{}", true)
                    raw.createNewBackupWithMetadata(source.db,
                        "maryk-rocksdb-backup:1\ntrue\nfalse\nmodels: {1: {name: A, name: B, keySize: 16}}", true)
                }
                finally { raw.close() }
            } finally { options.close() }
            repository = RocksDBBackupRepository.open(path)
            val unreadable = repository.unreadableBackupIds()
            assertEquals(2, unreadable.size)
            assertEquals(listOf(good.id), repository.backups().map { it.id })
            repository.purgeOldBackups(1)
            assertEquals(listOf(good.id), repository.backups().map { it.id })
            assertEquals(unreadable, repository.unreadableBackupIds())
            repository.restoreBackup(good.id, destination)
            assertFalse(backupPathExists("$destination/$INCOMPLETE_RESTORE_DIRECTORY"))
            val restored = RocksDBDataStore.open(relativePath = destination, dataModelsById = mapOf(1u to Log))
            try {
                assertEquals("healthy recovery point", restored.execute(Log.get(Log.key(value))).values.single().values { message })
                restored.verifyStorageChecksums()
            } finally { restored.close() }
            assertFailsWith<IllegalArgumentException> { repository.restoreBackup(unreadable.first(), "$destination-future") }
            assertFalse(backupPathExists("$destination-future"))
            unreadable.forEach { repository.deleteBackup(it) }
        } finally {
            source.close()
            repository.close()
            deleteFolder(sourcePath)
            deleteFolder(path)
            deleteFolder(destination)
        }
    }

    @Test
    fun metadataSizeLimitAndModernIncrementalSstIdentities() = runTest {
        assertFailsWith<IllegalArgumentException> {
            encodeBackupMetadata(true, true, "#".repeat(1024 * 1024))
        }
        for (malformed in listOf("scalar", "models: false", "models: {1: {name: MissingKeySize}}", "indexKeyFormatVersion: 3\nmodels: {}")) {
            assertFailsWith<IllegalArgumentException> {
                decodeBackupMetadata("maryk-rocksdb-backup:1\ntrue\nfalse\n$malformed")
            }
        }
        val unicode = "models: {1: {name: 'Unicode 🧪', keySize: 16}}"
        val encoded = encodeBackupMetadata(true, false, unicode)
        assertTrue(encoded.all { it.code < 128 })
        assertEquals(unicode, decodeBackupMetadata(encoded).storeMeta)
        assertEquals(unicode, decodeBackupMetadata("maryk-rocksdb-backup:1\ntrue\nfalse\n$unicode").storeMeta)
        val sourcePath = createTestDBFolder("physical-identities-source")
        val path = createTestDBFolder("physical-identities-backups")
        val source = RocksDBDataStore.open(relativePath = sourcePath, dataModelsById = mapOf(1u to Log))
        val repository = RocksDBBackupRepository.open(path)
        try {
            source.execute(Log.add(Log("modern shared file identity")))
            val first = repository.createBackup(source)
            val second = repository.createBackup(source)
            fun ssts(id: Int) = Regex("""shared_checksum/[^\s]+\.sst""")
                .findAll(assertNotNull(File.readText("$path/meta/$id"))).map { it.value }.toSet()
            val firstFiles = ssts(first.id)
            assertTrue(firstFiles.isNotEmpty())
            assertTrue(firstFiles.all { Regex(""".*_s[^/]+\.sst""").matches(it) }, "Modern SST backups must retain session-ID naming")
            assertEquals(firstFiles, ssts(second.id), "Unchanged SSTs must be shared across backup IDs")
        } finally {
            source.close()
            repository.close()
            deleteFolder(sourcePath)
            deleteFolder(path)
        }
    }

    @Test
    fun metadataRejectsAdditionalDocumentsAndInvalidFormatVersions() {
        for (metadata in listOf(
            "{models: {}}\n---\n{models: {1: {name: Hidden, keySize: 16}}}",
            "version: 0\nmodels: {}",
            "version: -1\nmodels: {}",
            "extension: {ignored: true}\nversion: 3\nmodels: {}",
            "extension: {ignored: true}\nindexKeyFormatVersion: 3\nmodels: {}",
        )) {
            assertFails { encodeBackupMetadata(true, false, metadata) }
            assertFails {
                decodeBackupMetadata("maryk-rocksdb-backup:1\ntrue\nfalse\n$metadata")
            }
        }
    }

    @Test
    fun directorySyncFailuresLeaveRestoreBlocked() = runTest {
        for (failAt in listOf(1, 2, 3)) {
            val path = createTestDBFolder("physical-sync-failure-$failAt")
            try {
                createNewBackupDirectory("$path/$INCOMPLETE_RESTORE_DIRECTORY")
                var calls = 0
                assertFailsWith<IllegalStateException> {
                    finishPhysicalRestore(path) { ++calls != failAt }
                }
                assertTrue(backupPathExists("$path/$INCOMPLETE_RESTORE_DIRECTORY"))
                assertFailsWith<IllegalStateException> {
                    RocksDBDataStore.open(relativePath = path, dataModelsById = mapOf(1u to Log))
                }
            } finally { deleteFolder(path) }
        }
    }

    @Test
    fun cancelledOwnerStillClosesRepositoryAndReleasesOwnership() = runTest {
        val path = createTestDBFolder("physical-cancel-close")
        val repository = RocksDBBackupRepository.open(path)
        val closing = launch {
            try {
                CompletableDeferred<Unit>().await()
            } finally {
                repository.close()
            }
        }
        // Enter the coroutine's try/finally before cancellation.
        testScheduler.runCurrent()
        closing.cancelAndJoin()
        try {
            assertFailsWith<IllegalStateException> { repository.backups() }
            val reopened = RocksDBBackupRepository.open(path)
            try { assertTrue(reopened.backups().isEmpty()) } finally { reopened.close() }
        } finally {
            repository.close()
            deleteFolder(path)
        }
    }

    @Test
    fun existingEmptyDestinationAndChangedLiveMetadataAreRejected() = runTest {
        val sourcePath = createTestDBFolder("physical-metadata-source")
        val path = createTestDBFolder("physical-metadata-backups")
        val destination = createTestDBFolder("physical-existing-destination")
        val source = RocksDBDataStore.open(relativePath = sourcePath, dataModelsById = mapOf(1u to Log))
        val repository = RocksDBBackupRepository.open(path)
        try {
            val backup = repository.createBackup(source)
            assertFailsWith<IllegalArgumentException> { repository.restoreBackup(backup.id, destination) }
            assertFalse(backupPathExists("$destination/$INCOMPLETE_RESTORE_DIRECTORY"))
            val metadataPath = "$sourcePath/MARYK_META.yml"
            val original = assertNotNull(File.readText(metadataPath))
            try {
                File.writeText(metadataPath, "models: {}")
                assertFailsWith<IllegalStateException> { repository.createBackup(source) }
                assertEquals(listOf(backup.id), repository.backups().map { it.id })
            } finally { File.writeText(metadataPath, original) }
            assertTrue(repository.createBackup(source).id > backup.id)
        } finally {
            source.close()
            repository.close()
            deleteFolder(sourcePath)
            deleteFolder(path)
            deleteFolder(destination)
        }
    }

    @Test
    fun encryptedRecoveryPreservesCiphertextAndHistoryWithOriginalKeys() = runTest {
        val sourcePath = createTestDBFolder("physical-encrypted-source")
        val path = createTestDBFolder("physical-encrypted-backups")
        val destination = "$path-restored"
        // Key material is supplied separately, never recovered from the backup repository.
        fun provider() = AesGcmHmacSha256EncryptionProvider(ByteArray(32) { it.toByte() }, ByteArray(32) { (it + 32).toByte() })
        suspend fun open(directory: String) = RocksDBDataStore.open(relativePath = directory,
            dataModelsById = mapOf(1u to PhysicalEncryptedRecord), fieldEncryptionProvider = provider())
        val source = open(sourcePath)
        val repository = RocksDBBackupRepository.open(path)
        try {
            val record = PhysicalEncryptedRecord.create { secret with "before" }
            val added = assertIs<AddSuccess<PhysicalEncryptedRecord>>(source.execute(PhysicalEncryptedRecord.add(record)).statuses.single())
            val snapshot = source.captureSnapshotVersion()
            source.execute(PhysicalEncryptedRecord.change(added.key.change(Change(PhysicalEncryptedRecord.ref { secret } with "after"))))
            val valueKey = added.key.bytes + PhysicalEncryptedRecord.ref { secret }.toStorageByteArray()
            val ciphertext = assertNotNull(source.db.get(source.getColumnFamilies(1u).table, valueKey))
            val backup = repository.createBackup(source)
            source.close()
            deleteFolder(sourcePath)
            repository.restoreBackup(backup.id, destination)
            val recovered = open(destination)
            try {
                assertContentEquals(ciphertext, recovered.db.get(recovered.getColumnFamilies(1u).table, valueKey))
                assertEquals("after", recovered.execute(PhysicalEncryptedRecord.get(added.key)).values.single().values { secret })
                assertEquals("before", recovered.execute(PhysicalEncryptedRecord.get(added.key, toVersion = snapshot)).values.single().values { secret })
                recovered.verifyStorageChecksums()
            } finally { recovered.close() }
        } finally {
            source.close()
            repository.close()
            deleteFolder(sourcePath)
            deleteFolder(path)
            deleteFolder(destination)
        }
    }

}


private object PhysicalEncryptedRecord : RootDataModel<PhysicalEncryptedRecord>(name = "PhysicalEncryptedRecord-🧪") {
    val secret by string(1u, sensitive = true)
}
