package maryk.datastore.rocksdb

import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import maryk.core.models.key
import maryk.core.query.changes.Change
import maryk.core.query.changes.change
import maryk.core.query.pairs.with
import maryk.core.query.requests.add
import maryk.core.query.requests.change
import maryk.core.query.requests.get
import maryk.core.query.requests.scan
import maryk.core.query.responses.statuses.AddSuccess
import maryk.createTestDBFolder
import maryk.deleteFolder
import maryk.file.File
import maryk.rocksdb.FlushOptions
import maryk.rocksdb.RocksDBException
import maryk.rocksdb.effectiveColumnFamilyOptions
import maryk.rocksdb.getSortedWalFiles
import maryk.rocksdb.tableProperties
import maryk.test.models.Log
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

class RocksDBApiWorkloadTest {
    @Test
    fun diagnosticsSnapshotsAndRecoveryAcrossFlushAndCompaction() = runTest(timeout = 3.minutes) {
        val folder = createTestDBFolder("api-workload")
        val tuning = RocksDBTuning(blockCacheBytes = 16L * 1024 * 1024, memtableBudgetBytes = 4L * 1024 * 1024)
        val models = mapOf(1u to Log)
        var store = RocksDBDataStore.open(tuning, relativePath = folder, dataModelsById = models, keepAllVersions = true)
        val values = (0 until 600).map { index ->
            Log("$index:${"Maryk API workload ".repeat(100)}", timestamp = LocalDateTime(2026, 11, 4, 0, index / 60, index % 60))
        }
        val keys = values.map { Log.key(it) }
        try {
            for (batch in values.chunked(100)) {
                val result = store.execute(Log.add(*batch.toTypedArray()))
                result.statuses.forEach { assertIs<AddSuccess<Log>>(it) }
            }
            val initialStorage = store.storageDiagnostics()
            assertTrue(initialStorage.walFiles >= 0)
            assertTrue(initialStorage.walBytes >= 0)
            val snapshot = store.captureSnapshotVersion()
            var family = store.getColumnFamilies(1u).table
            val messageKey = keys.first().bytes + Log.ref { message }.toStorageByteArray()
            store.createReadContext().use { context ->
                val before = assertNotNull(store.db.get(family, context.defaultReadOptions, messageKey))
                val changes = keys.take(200).mapIndexed { index, key -> key.change(Change(Log.ref { message } with "changed-$index")) }
                store.execute(Log.change(*changes.toTypedArray()))
                FlushOptions().setWaitForFlush(true).use { store.db.flush(it, store.storageUpgradeHandles) }
                assertTrue(store.db.tableProperties(family).isNotEmpty())
                assertTrue(store.db.effectiveColumnFamilyOptions(family).containsKey("write_buffer_size"))
                assertTrue(store.db.getSortedWalFiles().all { it.sizeFileBytes() >= 0 })
                store.storageUpgradeHandles.forEach { store.db.compactRange(it) }
                assertContentEquals(before, store.db.get(family, context.defaultReadOptions, messageKey))
                store.verifyStorageChecksums()
            }
            repeat(2) {
                val result = store.execute(Log.get(*keys.toTypedArray()))
                assertEquals(keys, result.values.map { it.key })
                assertEquals("changed-0", result.values.first().values { message })
            }
            val disk = store.storageDiagnostics()
            assertTrue(disk.liveSstFiles > 0)
            assertTrue(disk.liveSstBytes > 0)
            assertEquals(disk.liveSstBytes, disk.liveSstBytesByLevel.values.sum())
            val diagnostics = store.diagnostics()
            assertTrue(diagnostics.counters.getValue("blockCacheHits") > 0)
            assertTrue(diagnostics.memtableBudgetManaged)
            assertEquals(600, store.execute(Log.scan(limit = 1000u)).values.size)
            assertEquals(values.first().invoke { message }, store.execute(Log.get(keys.first(), toVersion = snapshot)).values.single().values { message })
            store.close()
            store = RocksDBDataStore.open(tuning, relativePath = folder, dataModelsById = models, keepAllVersions = true)
            family = store.getColumnFamilies(1u).table
            assertTrue(store.db.tableProperties(family).isNotEmpty())
            assertEquals("changed-0", store.execute(Log.get(keys.first())).values.single().values { message })
            assertEquals(values.first().invoke { message }, store.execute(Log.get(keys.first(), toVersion = snapshot)).values.single().values { message })
            store.verifyStorageChecksums()
        } finally {
            store.close()
            deleteFolder(folder)
        }
        assertFailsWith<IllegalStateException> { store.storageDiagnostics() }
    }

    @Test
    fun checksumVerificationRejectsDamagedSstData() = runTest {
        val folder = createTestDBFolder("damaged-sst-verification")
        var store = RocksDBDataStore.open(relativePath = folder, dataModelsById = mapOf(1u to Log))
        try {
            val family = store.getColumnFamilies(1u).table
            val key = ByteArray(Log.Meta.keyByteSize) { 1 }
            store.db.put(family, key, ByteArray(8192) { 42 })
            FlushOptions().setWaitForFlush(true).use { store.db.flush(it, family) }
            val file = store.db.getLiveFilesMetaData().first { it.columnFamilyName().contentEquals(family.getName()) }
            val path = file.path().trimEnd('/') + "/" + file.fileName().trimStart('/')
            store.close()
            val bytes = assertNotNull(File.readBytes(path))
            bytes[0] = (bytes[0].toInt() xor 1).toByte()
            File.writeBytes(path, bytes)
            // Opening may discover the damaged block before explicit verification; either path must fail.
            assertFailsWith<RocksDBException> {
                store = RocksDBDataStore.open(relativePath = folder, dataModelsById = mapOf(1u to Log))
                store.verifyStorageChecksums()
            }
        } finally {
            store.close()
            deleteFolder(folder)
        }
    }
}
