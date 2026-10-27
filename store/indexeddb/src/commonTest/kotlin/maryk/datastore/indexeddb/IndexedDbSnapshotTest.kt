package maryk.datastore.indexeddb

import kotlinx.coroutines.test.runTest
import maryk.core.clock.HLC
import maryk.core.models.key
import maryk.core.query.pairs.with
import maryk.core.query.requests.add
import maryk.core.query.requests.get
import maryk.core.query.responses.UpdateResponse
import maryk.core.query.responses.statuses.AddSuccess
import maryk.core.query.responses.updates.AdditionUpdate
import maryk.datastore.shared.DataStoreBackupChunk
import maryk.datastore.shared.DataStoreBackupManifest
import maryk.datastore.shared.DataStoreBackupWriter
import maryk.datastore.shared.backup
import maryk.test.models.SimpleMarykModel
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class IndexedDbSnapshotTest {
    @Test
    fun reopenedBackupIncludesPersistedFutureVersion() = runTest {
        installIndexedDbForTests()
        val databaseName = "maryk-snapshot-reopen-${Random.nextInt()}"
        val models = mapOf(1u to SimpleMarykModel)
        val values = SimpleMarykModel.create { value with "ha future version" }
        val key = SimpleMarykModel.key(values)
        val futureVersion = HLC(HLC().toPhysicalUnixTime() + 86_400_000uL, 0u).timestamp
        val original = IndexedDbDataStore.open(databaseName, models, keepAllVersions = true)
        try {
            original.processUpdate(UpdateResponse(SimpleMarykModel, AdditionUpdate(
                key, futureVersion, futureVersion, 0, false, values,
            )))
        } finally {
            original.close()
        }
        val reopened = IndexedDbDataStore.open(databaseName, models, keepAllVersions = true)
        try {
            val chunks = mutableListOf<DataStoreBackupChunk>()
            val manifest = reopened.backup(object : DataStoreBackupWriter {
                override suspend fun begin(manifest: DataStoreBackupManifest) = Unit
                override suspend fun write(chunk: DataStoreBackupChunk) { chunks += chunk }
                override suspend fun complete() = Unit
            })
            assertTrue(manifest.snapshotVersion > futureVersion)
            assertEquals(key, chunks.single().records.single().key)
            assertEquals(futureVersion, chunks.single().records.single().changes.single().version)
        } finally {
            reopened.close()
        }
    }

    @Test
    fun snapshotReservesCrossContextBoundaryBeforeJournalPolling() = runTest {
        installIndexedDbForTests()
        checkCrossContextReservation()
    }

    @Test
    fun leaseSnapshotReservesCrossContextBoundaryBeforeJournalPolling() = runTest {
        installIndexedDbForTests()
        withoutWebLocks { checkCrossContextReservation() }
    }

    private suspend fun checkCrossContextReservation() = withoutBroadcastChannelForTests {
        indexedDbJournalPollingPausedForTests = true
        val databaseName = "maryk-snapshot-context-${Random.nextInt()}"
        val models = mapOf(1u to SimpleMarykModel)
        val reader = IndexedDbDataStore.open(databaseName, models, keepAllVersions = true)
        val writer = IndexedDbDataStore.open(databaseName, models, keepAllVersions = true)
        try {
            val values = SimpleMarykModel.create { value with "ha external future version" }
            val key = SimpleMarykModel.key(values)
            val futureVersion = HLC(HLC().toPhysicalUnixTime() + 86_400_000uL, 0u).timestamp
            writer.processUpdate(UpdateResponse(SimpleMarykModel, AdditionUpdate(
                key, futureVersion, futureVersion, 0, false, values,
            )))
            val snapshot = reader.captureSnapshotVersion()
            assertTrue(snapshot > futureVersion)
            assertEquals(values, reader.execute(SimpleMarykModel.get(key, toVersion = snapshot)).values.single().values)
            val next = assertIs<AddSuccess<SimpleMarykModel>>(writer.execute(
                SimpleMarykModel.add(SimpleMarykModel.create { value with "ha after snapshot" }),
            ).statuses.single())
            assertTrue(next.version > snapshot)
            assertTrue(reader.execute(SimpleMarykModel.get(next.key, toVersion = snapshot)).values.isEmpty())
        } finally {
            writer.close()
            reader.close()
            indexedDbJournalPollingPausedForTests = false
        }
    }
}
