package maryk.datastore.rocksdb

import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import maryk.core.clock.HLC
import maryk.core.models.key
import maryk.core.query.changes.Change
import maryk.core.query.changes.change
import maryk.core.query.pairs.with
import maryk.core.query.requests.add
import maryk.core.query.requests.AtomicRequest
import maryk.core.query.requests.change
import maryk.core.query.requests.get
import maryk.core.query.responses.UpdateResponse
import maryk.core.query.responses.AtomicStatus
import maryk.core.query.responses.TransactionStatus
import maryk.core.query.responses.statuses.AddSuccess
import maryk.core.query.responses.updates.AdditionUpdate
import maryk.core.query.responses.updates.ChangeUpdate
import maryk.core.query.responses.updates.RemovalReason.HardDelete
import maryk.core.query.responses.updates.RemovalUpdate
import maryk.createTestDBFolder
import maryk.deleteFolder
import maryk.datastore.rocksdb.model.modelHlcWatermarkKey
import maryk.datastore.rocksdb.model.modelTransactionHlcWatermarkRepairCompleteKey
import maryk.datastore.shared.migration.StorageMaintenanceMetadataKey
import maryk.datastore.shared.beginTransaction
import maryk.datastore.shared.executeAtomic
import maryk.test.models.Log
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertContentEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RocksDBReplicationClockTest {
    @Test
    fun previewAndRejectedAtomicDoNotPublishWatermarksOrReceipts() = runTest {
        val folder = createTestDBFolder("uncommitted-hlc-watermark")
        val dataStore = RocksDBDataStore.open(relativePath = folder, dataModelsById = mapOf(1u to Log))
        val original = Log("original", timestamp = LocalDateTime(2026, 2, 2, 0, 0))
        val duplicate = Log("existing", timestamp = LocalDateTime(2026, 2, 2, 1, 0))
        val key = Log.key(original)
        try {
            dataStore.execute(Log.add(original, duplicate))
            val model = dataStore.getColumnFamilies(1u).model
            val watermark = dataStore.db.get(model, modelHlcWatermarkKey)
            val change = Log.change(key.change(Change(Log.ref { message } with "uncommitted")))
            val transaction = dataStore.beginTransaction()
            transaction.execute(change)
            assertContentEquals(watermark, dataStore.db.get(model, modelHlcWatermarkKey))
            assertNull(dataStore.db.get(dataStore.transactionMetadataHandle, "maryk.transaction/${transaction.id}".encodeToByteArray()))
            transaction.rollback()
            assertEquals(AtomicStatus.Rejected, dataStore.executeAtomic(AtomicRequest("rejected-clock", change, Log.add(duplicate))).status)
            assertContentEquals(watermark, dataStore.db.get(model, modelHlcWatermarkKey))
            assertNull(dataStore.db.get(dataStore.transactionMetadataHandle, "maryk.transaction/rejected-clock".encodeToByteArray()))
            assertEquals("original", dataStore.execute(Log.get(key)).values.single().values { message })
        } finally {
            dataStore.close()
            deleteFolder(folder)
        }
    }

    @Test
    fun reopenedSnapshotIncludesAtomicCommits() = runTest {
        checkCommittedWatermark(useTransaction = false, repairLegacyWatermark = false)
    }

    @Test
    fun reopenedSnapshotIncludesTransactionCommits() = runTest {
        checkCommittedWatermark(useTransaction = true, repairLegacyWatermark = false)
    }

    @Test
    fun reopenRepairsLegacyWatermarkBehindCommittedRecords() = runTest {
        checkCommittedWatermark(useTransaction = false, repairLegacyWatermark = true)
    }

    private suspend fun checkCommittedWatermark(useTransaction: Boolean, repairLegacyWatermark: Boolean) {
        val folder = createTestDBFolder("committed-hlc-watermark")
        val models = mapOf(1u to Log)
        val values = Log("replicated", timestamp = LocalDateTime(2026, 2, 2, 0, 0))
        val key = Log.key(values)
        val futureVersion = HLC(HLC().toPhysicalUnixTime() + 86_400_000uL, 0u).timestamp
        var dataStore = RocksDBDataStore.open(relativePath = folder, dataModelsById = models, keepAllVersions = true)
        try {
            dataStore.processUpdate(UpdateResponse(Log, AdditionUpdate(key, futureVersion, futureVersion, 0, false, values)))
            repeat(2) { index ->
                val request = Log.change(key.change(Change(Log.ref { message } with "committed-$index")))
                if (useTransaction) {
                    val transaction = dataStore.beginTransaction()
                    transaction.execute(request)
                    assertEquals(TransactionStatus.Committed, transaction.commit().status)
                } else {
                    assertEquals(AtomicStatus.Committed, dataStore.executeAtomic(AtomicRequest("clock-$index", request)).status)
                }
            }
            val latestVersion = dataStore.execute(Log.get(key)).values.single().lastVersion
            if (repairLegacyWatermark) {
                val model = dataStore.getColumnFamilies(1u).model
                dataStore.db.put(model, modelHlcWatermarkKey, HLC.toStorageBytes(HLC(futureVersion)))
                dataStore.db.delete(model, modelTransactionHlcWatermarkRepairCompleteKey)
                dataStore.db.delete(model, StorageMaintenanceMetadataKey)
            }
            dataStore.close()
            dataStore = RocksDBDataStore.open(relativePath = folder, dataModelsById = models, keepAllVersions = true)
            val snapshot = dataStore.captureSnapshotVersion()
            assertTrue(snapshot > latestVersion)
            assertEquals("committed-1", dataStore.execute(Log.get(key, toVersion = snapshot)).values.single().values { message })
            val next = assertIs<AddSuccess<Log>>(dataStore.execute(
                Log.add(Log("after reopen", timestamp = LocalDateTime(2026, 2, 2, 1, 0))),
            ).statuses.single())
            assertTrue(next.version > snapshot)
        } finally {
            dataStore.close()
            deleteFolder(folder)
        }
    }

    @Test
    fun staleRemovalDoesNotDeleteRecordChangedAtNewerVersion() = runTest {
        val folder = createTestDBFolder("stale-replicated-removal")
        val dataStore = RocksDBDataStore.open(
            relativePath = folder,
            dataModelsById = mapOf(1u to Log),
        )
        val values = Log("created", timestamp = LocalDateTime(2026, 2, 1, 0, 0))
        val key = Log.key(values)
        try {
            dataStore.processUpdate(
                UpdateResponse(Log, AdditionUpdate(key, 10uL, 10uL, 0, false, values))
            )
            dataStore.processUpdate(
                UpdateResponse(
                    Log,
                    ChangeUpdate(
                        key,
                        30uL,
                        0,
                        listOf(Change(Log.ref { message } with "newer change")),
                    ),
                )
            )
            dataStore.processUpdate(
                UpdateResponse(Log, RemovalUpdate(key, 20uL, HardDelete))
            )

            val stored = dataStore.execute(Log.get(key)).values.single()
            assertEquals("newer change", stored.values { message })
            assertEquals(30uL, stored.lastVersion)
        } finally {
            dataStore.close()
            deleteFolder(folder)
        }
    }

    @Test
    fun reopenedStoreAdvancesBeyondDurableReplicatedVersion() = runTest {
        val folder = createTestDBFolder("durable-hlc-watermark")
        val futureVersion = HLC(HLC().toPhysicalUnixTime() + 60_000uL, 0u).timestamp
        val replicatedValues = Log("future replicated", timestamp = LocalDateTime(2026, 2, 2, 0, 0))
        var dataStore = RocksDBDataStore.open(
            relativePath = folder,
            dataModelsById = mapOf(1u to Log),
        )
        try {
            dataStore.processUpdate(
                UpdateResponse(
                    Log,
                    AdditionUpdate(
                        Log.key(replicatedValues),
                        futureVersion,
                        futureVersion,
                        0,
                        false,
                        replicatedValues,
                    ),
                )
            )
            dataStore.close()

            dataStore = RocksDBDataStore.open(
                relativePath = folder,
                dataModelsById = mapOf(1u to Log),
            )
            val snapshotVersion = dataStore.captureSnapshotVersion()
            val localStatus = assertIs<AddSuccess<Log>>(
                dataStore.execute(
                    Log.add(Log("after reopen", timestamp = LocalDateTime(2026, 2, 2, 1, 0)))
                ).statuses.single()
            )

            assertTrue(snapshotVersion > futureVersion)
            assertTrue(localStatus.version > futureVersion)
        } finally {
            dataStore.close()
            deleteFolder(folder)
        }
    }
}
