package maryk.datastore.rocksdb

import kotlinx.coroutines.test.runTest
import maryk.core.models.key
import maryk.core.query.changes.Change
import maryk.core.query.changes.change
import maryk.core.query.pairs.with
import maryk.core.query.requests.add
import maryk.core.query.requests.change
import maryk.core.query.requests.get
import maryk.createTestDBFolder
import maryk.deleteFolder
import maryk.file.File
import maryk.test.models.Log
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.time.Duration.Companion.minutes

class WindowsUnicodeBackupTest {
    @Test
    fun restoresUnicodeStoresAndRepositoriesIncludingSupplementaryCharacters() = runTest(timeout = 3.minutes) {
        for (suffix in listOf("café", "İstanbul", "日本語", "emoji-😀")) {
            val parent = createTestDBFolder("windows-unicode-$suffix")
            val sourcePath = "$parent/source-$suffix"
            val repositoryPath = "$parent/backups-$suffix"
            val destination = "$parent/restored-$suffix"
            createNewBackupDirectory(sourcePath)
            createNewBackupDirectory(repositoryPath)
            try {
                val value = Log("before")
                val key = Log.key(value)
                var snapshot = 0uL
                var repository = RocksDBBackupRepository.open(repositoryPath)
                val backup = try {
                    val store = RocksDBDataStore.open(relativePath = sourcePath, dataModelsById = mapOf(1u to Log))
                    try {
                        store.execute(Log.add(value))
                        snapshot = store.captureSnapshotVersion()
                        store.execute(Log.change(key.change(Change(Log.ref { message } with "after"))))
                        assertNotNull(File.size("$sourcePath/CURRENT"), "Engine files must use the requested Unicode path")
                        repository.createBackup(store)
                    } finally { store.close() }
                } finally { repository.close() }
                deleteFolder(sourcePath)
                repository = RocksDBBackupRepository.open(repositoryPath)
                try {
                    assertEquals(listOf(backup), repository.backups())
                    repository.restoreBackup(backup.id, destination)
                } finally { repository.close() }
                val restored = RocksDBDataStore.open(relativePath = destination, dataModelsById = mapOf(1u to Log))
                try {
                    assertEquals("after", restored.execute(Log.get(key)).values.single().values { message })
                    assertEquals("before", restored.execute(Log.get(key, toVersion = snapshot)).values.single().values { message })
                    restored.verifyStorageChecksums()
                    restored.execute(Log.add(Log("after recovery")))
                } finally { restored.close() }
            } finally { deleteFolder(parent) }
        }
    }
}
