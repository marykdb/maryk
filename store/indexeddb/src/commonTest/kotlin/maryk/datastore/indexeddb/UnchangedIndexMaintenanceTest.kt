package maryk.datastore.indexeddb

import kotlinx.coroutines.test.runTest
import maryk.core.models.RootDataModel
import maryk.core.properties.definitions.string
import maryk.core.properties.types.Bytes
import maryk.core.query.changes.Change
import maryk.core.query.changes.IndexChange
import maryk.core.query.changes.IndexUpdate
import maryk.core.query.changes.ObjectSoftDeleteChange
import maryk.core.query.changes.change
import maryk.core.query.filters.Equals
import maryk.core.query.orders.ascending
import maryk.core.query.pairs.with
import maryk.core.query.requests.add
import maryk.core.query.requests.change
import maryk.core.query.requests.scan
import maryk.core.query.responses.statuses.AddSuccess
import maryk.core.query.responses.statuses.ChangeSuccess
import maryk.datastore.shared.updates.Update
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

private object MaintainedIndexRecord : RootDataModel<MaintainedIndexRecord>(indexes = { listOf(MaintainedIndexRecord.name.ref()) }) {
    val name by string(1u)
    val status by string(2u)
}

class UnchangedIndexMaintenanceTest {
    @Test
    fun unrelatedChangesKeepIndexHistoryAndDeletionTransitionsRemainReplayable() = runTest {
        installIndexedDbForTests()
        withoutBroadcastChannelForTests {
            val paused = indexedDbJournalPollingPausedForTests
            indexedDbJournalPollingPausedForTests = true
            try {
                val database = "unchanged-index-${Random.nextInt()}"
                val models = mapOf(1u to MaintainedIndexRecord)
                val store = IndexedDbDataStore.open(database, models, keepAllVersions = true)
                // Keep a peer consumer so journal payloads remain available for assertions.
                val peer = IndexedDbDataStore.open(database, models, keepAllVersions = true)
                try {
                    val added = assertIs<AddSuccess<MaintainedIndexRecord>>(store.execute(MaintainedIndexRecord.add(
                        MaintainedIndexRecord.create { name with "before"; status with "initial" }
                    )).statuses.single())
                    val keys = store.byteStore.getAllKeys("hi:1").map { Bytes(it) }
                    val changed = assertIs<ChangeSuccess<MaintainedIndexRecord>>(store.execute(MaintainedIndexRecord.change(
                        added.key.change(Change(MaintainedIndexRecord.status.ref() with "updated"))
                    )).statuses.single())
                    assertEquals(keys, store.byteStore.getAllKeys("hi:1").map { Bytes(it) })
                    val historical = store.execute(MaintainedIndexRecord.scan(
                        where = Equals(MaintainedIndexRecord.name.ref() with "before"),
                        order = MaintainedIndexRecord.name.ref().ascending(), toVersion = changed.version,
                    ))
                    assertEquals("updated", historical.values.single().values { status })
                    val moved = assertIs<ChangeSuccess<MaintainedIndexRecord>>(store.execute(MaintainedIndexRecord.change(
                        added.key.change(Change(MaintainedIndexRecord.name.ref() with "after"))
                    )).statuses.single())
                    assertEquals(1, store.execute(MaintainedIndexRecord.scan(
                        where = Equals(MaintainedIndexRecord.name.ref() with "before"),
                        order = MaintainedIndexRecord.name.ref().ascending(), toVersion = changed.version,
                    )).values.size)
                    assertEquals(1, store.execute(MaintainedIndexRecord.scan(
                        where = Equals(MaintainedIndexRecord.name.ref() with "after"),
                        order = MaintainedIndexRecord.name.ref().ascending(), toVersion = moved.version,
                    )).values.size)
                    val deleted = assertIs<ChangeSuccess<MaintainedIndexRecord>>(store.execute(MaintainedIndexRecord.change(
                        added.key.change(ObjectSoftDeleteChange(true))
                    )).statuses.single())
                    assertEquals(0, store.execute(MaintainedIndexRecord.scan(
                        where = Equals(MaintainedIndexRecord.name.ref() with "after"),
                        order = MaintainedIndexRecord.name.ref().ascending(), toVersion = deleted.version,
                    )).values.size)
                    store.execute(MaintainedIndexRecord.change(added.key.change(ObjectSoftDeleteChange(false))))
                    assertEquals(1, store.execute(MaintainedIndexRecord.scan(
                        where = Equals(MaintainedIndexRecord.name.ref() with "after"),
                        order = MaintainedIndexRecord.name.ref().ascending(),
                    )).values.size)
                    val journal = store.byteStore.scan(CommitJournalStoreName).last()
                    val update = assertIs<Update.Change<*>>(decodeCommitJournalEntry(models, store.sensitiveFields,
                        journal.first, journal.second))
                    assertEquals(1, update.changes.filterIsInstance<IndexChange>().flatMap { it.changes }.filterIsInstance<IndexUpdate>().size)
                } finally { peer.close(); store.close() }
            } finally { indexedDbJournalPollingPausedForTests = paused }
        }
    }
}
