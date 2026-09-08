package maryk.datastore.rocksdb

import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import maryk.core.clock.HLC
import maryk.core.models.key
import maryk.core.query.changes.Change
import maryk.core.query.pairs.with
import maryk.core.query.requests.add
import maryk.core.query.requests.get
import maryk.core.query.responses.UpdateResponse
import maryk.core.query.responses.statuses.AddSuccess
import maryk.core.query.responses.updates.AdditionUpdate
import maryk.core.query.responses.updates.ChangeUpdate
import maryk.core.query.responses.updates.RemovalReason.HardDelete
import maryk.core.query.responses.updates.RemovalUpdate
import maryk.createTestDBFolder
import maryk.deleteFolder
import maryk.test.models.Log
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class RocksDBReplicationClockTest {
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
                        listOf(Change(Log { message::ref } with "newer change")),
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
