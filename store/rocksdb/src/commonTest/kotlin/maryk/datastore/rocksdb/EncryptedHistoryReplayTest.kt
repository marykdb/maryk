package maryk.datastore.rocksdb

import kotlinx.coroutines.test.runTest
import maryk.core.models.RootDataModel
import maryk.core.models.fromChanges
import maryk.core.properties.definitions.string
import maryk.core.query.changes.Change
import maryk.core.query.changes.change
import maryk.core.query.pairs.with
import maryk.core.query.requests.add
import maryk.core.query.requests.change
import maryk.core.query.requests.get
import maryk.core.query.requests.getChanges
import maryk.core.query.responses.statuses.AddSuccess
import maryk.core.query.responses.statuses.ChangeSuccess
import maryk.createTestDBFolder
import maryk.datastore.shared.DataStoreBackupChunk
import maryk.datastore.shared.DataStoreBackupManifest
import maryk.datastore.shared.DataStoreBackupReader
import maryk.datastore.shared.DataStoreBackupWriter
import maryk.datastore.shared.backup
import maryk.datastore.shared.encryption.AesGcmHmacSha256EncryptionProvider
import maryk.datastore.shared.restore
import maryk.deleteFolder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

private object EncryptedHistoryRecord : RootDataModel<EncryptedHistoryRecord>() {
    val label by string(1u)
    val secret by string(2u, sensitive = true, unique = true, required = false)
}

class EncryptedHistoryReplayTest {
    @Test
    fun reopenedEncryptedCellsExportPlaintextHistoryAndRestoreWithAnotherKey() = runTest {
        val sourcePath = createTestDBFolder("encrypted-history-source")
        val targetPath = createTestDBFolder("encrypted-history-target")
        fun provider(seed: Int) = AesGcmHmacSha256EncryptionProvider(
            ByteArray(32) { (seed + it).toByte() }, ByteArray(32) { (seed + 32 + it).toByte() },
        )
        suspend fun open(path: String, seed: Int) = RocksDBDataStore.open(
            relativePath = path, dataModelsById = mapOf(1u to EncryptedHistoryRecord),
            keepAllVersions = true, fieldEncryptionProvider = provider(seed),
        )
        var source = open(sourcePath, 1)
        val target = open(targetPath, 101)
        try {
            val before = EncryptedHistoryRecord.create { label with "same"; secret with "before" }
            val after = EncryptedHistoryRecord.create { label with "same"; secret with "after" }
            val added = assertIs<AddSuccess<EncryptedHistoryRecord>>(source.execute(EncryptedHistoryRecord.add(before)).statuses.single())
            val changed = assertIs<ChangeSuccess<EncryptedHistoryRecord>>(source.execute(EncryptedHistoryRecord.change(added.key.change(
                Change(EncryptedHistoryRecord.ref { secret } with "after"),
            ))).statuses.single())
            source.close()
            source = open(sourcePath, 1)
            val latest = source.execute(EncryptedHistoryRecord.getChanges(added.key)).changes.single().changes
            assertEquals(after, EncryptedHistoryRecord.fromChanges(null, latest.flatMap { it.changes }))
            val deleted = assertIs<ChangeSuccess<EncryptedHistoryRecord>>(source.execute(EncryptedHistoryRecord.change(added.key.change(
                Change(EncryptedHistoryRecord.ref { secret } with null),
            ))).statuses.single())
            source.close()
            source = open(sourcePath, 1)

            val history = source.execute(EncryptedHistoryRecord.getChanges(added.key, maxVersions = 10u)).changes.single().changes
            var replayed = EncryptedHistoryRecord.fromChanges(null, history.first().changes)
            assertEquals(before, replayed)
            replayed = replayed.change(history.first { it.version == changed.version }.changes)
            assertEquals(after, replayed)
            replayed = replayed.change(history.first { it.version == deleted.version }.changes)
            assertEquals(EncryptedHistoryRecord.create { label with "same" }, replayed)
            val current = source.execute(EncryptedHistoryRecord.getChanges(added.key)).changes.single().changes
            assertEquals(replayed, EncryptedHistoryRecord.fromChanges(null, current.flatMap { it.changes }))

            val backup = EncryptedReplayBackup()
            source.backup(backup, batchSize = 1u)
            target.restore(backup)
            for ((version, expected) in listOf(added.version to before, changed.version to after, deleted.version to replayed)) {
                assertEquals(expected, target.execute(EncryptedHistoryRecord.get(added.key, toVersion = version)).values.single().values)
            }
        } finally {
            source.close()
            target.close()
            deleteFolder(sourcePath)
            deleteFolder(targetPath)
        }
    }
}

private class EncryptedReplayBackup : DataStoreBackupWriter, DataStoreBackupReader {
    override lateinit var manifest: DataStoreBackupManifest
    private val chunks = mutableListOf<DataStoreBackupChunk>()
    override suspend fun begin(manifest: DataStoreBackupManifest) { this.manifest = manifest }
    override suspend fun write(chunk: DataStoreBackupChunk) { chunks += chunk }
    override suspend fun complete() = Unit
    override suspend fun read(consumer: suspend (DataStoreBackupChunk) -> Unit) { chunks.forEach { consumer(it) } }
}
