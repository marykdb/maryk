package maryk.datastore.indexeddb

import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import maryk.core.models.key
import maryk.core.properties.types.Bytes
import maryk.core.query.changes.Change
import maryk.core.query.changes.DataObjectVersionedChange
import maryk.core.query.changes.IndexChange
import maryk.core.query.changes.IndexDelete
import maryk.core.query.changes.IndexUpdate
import maryk.core.query.changes.VersionedChanges
import maryk.core.query.pairs.with
import maryk.core.query.changes.change
import maryk.core.query.requests.change
import maryk.core.query.requests.add
import maryk.core.query.requests.get
import maryk.core.query.responses.statuses.AddSuccess
import maryk.datastore.indexeddb.processors.encodeVersionedChange
import maryk.datastore.shared.updates.Update
import maryk.test.models.SimpleMarykModel
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertFalse

class JournalReadOptimizationTest {
    @Test
    fun soleConsumerBulkPruningLeavesAConsistentFloorForNewPeers() = runTest {
        installIndexedDbForTests()
        withoutBroadcastChannelForTests {
            val paused = indexedDbJournalPollingPausedForTests
            indexedDbJournalPollingPausedForTests = true
            val database = "sole-bulk-journal-${Random.nextInt()}"
            val writer = IndexedDbDataStore.open(database, mapOf(1u to SimpleMarykModel))
            try {
                val entries = Array(64) { index ->
                    SimpleMarykModel.key(ByteArray(SimpleMarykModel.Meta.keyByteSize) { if (it == 0) index.toByte() else 0 }) to
                        SimpleMarykModel.create { value with "ha-$index" }
                }
                val result = writer.execute(SimpleMarykModel.add(*entries))
                val statuses = result.statuses.map { assertIs<AddSuccess<SimpleMarykModel>>(it) }
                assertEquals(0, writer.byteStore.getAllKeys(CommitJournalStoreName).size)
                assertContentEquals(createCommitJournalKey(statuses.last().version, 63u),
                    writer.byteStore.get("meta", CommitJournalFloorMetadataKey))
                val peer = IndexedDbDataStore.open(database, mapOf(1u to SimpleMarykModel))
                try {
                    peer.executeFlow(SimpleMarykModel.get(entries.first().first)).first()
                    assertEquals(64, peer.execute(SimpleMarykModel.get(*entries.map { it.first }.toTypedArray())).values.size)
                } finally { peer.close() }
            } finally {
                writer.close()
                indexedDbJournalPollingPausedForTests = paused
            }
        }
    }
    @Test
    fun unchangedJournalClockAvoidsAcknowledgingActiveReadsAndPeerCommitsStillCatchUp() = runTest {
        installIndexedDbForTests()
        withoutBroadcastChannelForTests {
            val paused = indexedDbJournalPollingPausedForTests
            indexedDbJournalPollingPausedForTests = true
            val database = "active-read-journal-${Random.nextInt()}"
            val reader = IndexedDbDataStore.open(database, mapOf(1u to SimpleMarykModel))
            val writer = IndexedDbDataStore.open(database, mapOf(1u to SimpleMarykModel))
            val added = assertIs<AddSuccess<SimpleMarykModel>>(writer.execute(
                SimpleMarykModel.add(SimpleMarykModel.create { value with "ha-original" })
            ).statuses.single())
            val ready = CompletableDeferred<Unit>()
            val changed = CompletableDeferred<Unit>()
            var responses = 0
            val listener = launch(journalListenerDispatcher) {
                reader.executeFlow(SimpleMarykModel.get(added.key)).collect {
                    if (++responses == 1) ready.complete(Unit) else changed.complete(Unit)
                }
            }
            try {
                withContext(journalListenerDispatcher) { withTimeout(5_000) { ready.await() } }
                reader.execute(SimpleMarykModel.get(added.key))
                val consumerKey = reader.byteStore.contextId().encodeToByteArray()
                val current = decodeJournalConsumer(assertNotNull(reader.byteStore.get(CommitConsumerStoreName, consumerKey)))
                val staleHeartbeat = encodeJournalConsumer(0uL, current.cursor)
                reader.byteStore.put(CommitConsumerStoreName, consumerKey, staleHeartbeat)
                reader.execute(SimpleMarykModel.get(added.key))
                assertContentEquals(staleHeartbeat, reader.byteStore.get(CommitConsumerStoreName, consumerKey))
                reader.byteStore.put(CommitConsumerStoreName, consumerKey,
                    encodeJournalConsumer(reader.byteStore.currentEpochMillis(), current.cursor))
                writer.execute(SimpleMarykModel.change(added.key.change(
                    Change(SimpleMarykModel.ref { value } with "ha-changed")
                )))
                assertEquals("ha-changed", reader.execute(SimpleMarykModel.get(added.key)).values.single().values { value })
                withContext(journalListenerDispatcher) { withTimeout(5_000) { changed.await() } }
            } finally {
                listener.cancelAndJoin()
                reader.close()
                writer.close()
                indexedDbJournalPollingPausedForTests = paused
            }
        }
    }

    @Test
    fun unchangedClockRefreshesActiveConsumerAfterLongPause() = runTest {
        installIndexedDbForTests()
        withoutBroadcastChannelForTests {
            val paused = indexedDbJournalPollingPausedForTests
            indexedDbJournalPollingPausedForTests = true
            val reader = IndexedDbDataStore.open("resume-journal-${Random.nextInt()}", mapOf(1u to SimpleMarykModel))
            val ready = CompletableDeferred<Unit>()
            var listener: Job? = null
            try {
                val epoch = reader.byteStore.currentEpochMillis()
                withFixedWallClockForTests(epoch.toDouble()) {
                    val added = assertIs<AddSuccess<SimpleMarykModel>>(reader.execute(
                        SimpleMarykModel.add(SimpleMarykModel.create { value with "ha-resume" })
                    ).statuses.single())
                    listener = launch(journalListenerDispatcher) {
                        reader.executeFlow(SimpleMarykModel.get(added.key)).collect { ready.complete(Unit) }
                    }
                    withContext(journalListenerDispatcher) { withTimeout(5_000) { ready.await() } }
                    reader.execute(SimpleMarykModel.get(added.key))
                    val consumerKey = reader.byteStore.contextId().encodeToByteArray()
                    val resumedAt = epoch + JournalConsumerTimeoutMillis / 2uL + 1uL
                    withFixedWallClockForTests(resumedAt.toDouble()) {
                        reader.execute(SimpleMarykModel.get(added.key))
                        assertEquals(resumedAt, decodeJournalConsumer(assertNotNull(
                            reader.byteStore.get(CommitConsumerStoreName, consumerKey)
                        )).heartbeatMillis)
                    }
                }
            } finally {
                listener?.cancelAndJoin()
                reader.close()
                indexedDbJournalPollingPausedForTests = paused
            }
        }
    }

    @Test
    fun bulkRequestsKeepPerRecordCommitsAndOrderedJournalEntries() = runTest {
        installIndexedDbForTests()
        withoutBroadcastChannelForTests {
            val paused = indexedDbJournalPollingPausedForTests
            indexedDbJournalPollingPausedForTests = true
            val database = "bulk-journal-${Random.nextInt()}"
            val reader = IndexedDbDataStore.open(database, mapOf(1u to SimpleMarykModel))
            val writer = IndexedDbDataStore.open(database, mapOf(1u to SimpleMarykModel))
            try {
                val entries = Array(64) { index ->
                    SimpleMarykModel.key(ByteArray(SimpleMarykModel.Meta.keyByteSize) { if (it == 0) index.toByte() else 0 }) to
                        SimpleMarykModel.create { value with "ha-$index" }
                }
                val result = writer.execute(SimpleMarykModel.add(*entries))
                val statuses = result.statuses.map { assertIs<AddSuccess<SimpleMarykModel>>(it) }
                assertEquals(64, statuses.size)
                val journal = writer.byteStore.getAllKeys(CommitJournalStoreName)
                assertEquals(64, journal.size)
                assertContentEquals(createCommitJournalKey(statuses.first().version, 0u), journal.first())
                assertContentEquals(createCommitJournalKey(statuses.last().version, 63u), journal.last())
                assertEquals(64, reader.execute(SimpleMarykModel.get(*entries.map { it.first }.toTypedArray())).values.size)
            } finally {
                reader.close()
                writer.close()
                indexedDbJournalPollingPausedForTests = paused
            }
        }
    }
    @Test
    fun ordinaryReadsAvoidJournalAcknowledgementsButFlowSnapshotsCatchUp() = runTest {
        installIndexedDbForTests()
        withoutBroadcastChannelForTests {
            val paused = indexedDbJournalPollingPausedForTests
            indexedDbJournalPollingPausedForTests = true
            val database = "read-without-journal-${Random.nextInt()}"
            val reader = IndexedDbDataStore.open(database, mapOf(1u to SimpleMarykModel))
            val writer = IndexedDbDataStore.open(database, mapOf(1u to SimpleMarykModel))
            try {
                val consumerKey = reader.byteStore.contextId().encodeToByteArray()
                val before = assertNotNull(reader.byteStore.get(CommitConsumerStoreName, consumerKey))
                val added = assertIs<AddSuccess<SimpleMarykModel>>(writer.execute(
                    SimpleMarykModel.add(SimpleMarykModel.create { value with "ha-first" })
                ).statuses.single())
                assertEquals("ha-first", reader.execute(SimpleMarykModel.get(added.key)).values.single().values { value })
                assertContentEquals(before, reader.byteStore.get(CommitConsumerStoreName, consumerKey))
                reader.executeFlow(SimpleMarykModel.get(added.key)).first()
                val caughtUp = assertNotNull(reader.byteStore.get(CommitConsumerStoreName, consumerKey))
                assertFalse(decodeJournalConsumer(before).cursor.contentEquals(decodeJournalConsumer(caughtUp).cursor))
                val second = assertIs<AddSuccess<SimpleMarykModel>>(writer.execute(
                    SimpleMarykModel.add(SimpleMarykModel.create { value with "ha-second" })
                ).statuses.single())
                assertEquals("ha-second", reader.execute(SimpleMarykModel.get(second.key)).values.single().values { value })
                assertContentEquals(caughtUp, reader.byteStore.get(CommitConsumerStoreName, consumerKey))
            } finally {
                reader.close()
                writer.close()
                indexedDbJournalPollingPausedForTests = paused
            }
        }
    }
    @Test
    fun keyEnumerationUsesNativeCapabilityAndPreservesFallbackAndStagedWrites() = runTest {
        installIndexedDbForTests()
        val native = openIndexedDbByteStore("journal-keys-${Random.nextInt()}", setOf("rows"))
        try {
            native.put("rows", byteArrayOf(1), ByteArray(1_048_576))
            native.put("rows", byteArrayOf(1, 0), byteArrayOf(2))
            native.put("rows", byteArrayOf(255.toByte()), byteArrayOf(3))
            val expected = listOf(byteArrayOf(1), byteArrayOf(1, 0), byteArrayOf(255.toByte()))
            val keyOnly = object : IndexedDbByteStore by native, IndexedDbKeyReadStore {
                override suspend fun getAllKeys(storeName: String) = (native as IndexedDbKeyReadStore).getAllKeys(storeName)
                override suspend fun scan(storeName: String, startKey: ByteArray?, includeStart: Boolean,
                    endKey: ByteArray?, includeEnd: Boolean, reverse: Boolean, limit: UInt): List<Pair<ByteArray, ByteArray>> =
                    error("Key enumeration must not read journal payloads")
            }
            val routed = AtomicAwareIndexedDbByteStore(keyOnly, Any()).getAllKeys("rows")
            for (index in expected.indices) assertContentEquals(expected[index], routed[index])
            assertEquals(expected.size, routed.size)
            val fallback = object : IndexedDbByteStore by native {}
            val fallbackKeys = fallback.getAllKeys("rows")
            for (index in expected.indices) assertContentEquals(expected[index], fallbackKeys[index])
            val staged = StagedIndexedDbByteStore(native)
            staged.delete("rows", byteArrayOf(1))
            staged.put("rows", byteArrayOf(0), byteArrayOf(4))
            val stagedKeys = staged.getAllKeys("rows")
            assertContentEquals(byteArrayOf(0), stagedKeys.first())
            assertEquals(3, stagedKeys.size)
            assertContentEquals(expected.last(), stagedKeys.last())
        } finally { native.close() }
    }

    @Test
    fun largeChangeJournalKeepsItsOrdinaryAndIndexReplayFormat() = runTest {
        val key = SimpleMarykModel.key(ByteArray(SimpleMarykModel.Meta.keyByteSize))
        val ordinary = Change(SimpleMarykModel.ref { value } with ("ha" + "x".repeat(131_072)))
        val indexes = IndexChange(List(128) { index ->
            if (index % 2 == 0) IndexUpdate(Bytes(byteArrayOf(1)), Bytes(byteArrayOf(index.toByte())), null)
            else IndexDelete(Bytes(byteArrayOf(1)), Bytes(byteArrayOf(index.toByte())))
        })
        val versioned = DataObjectVersionedChange(key, changes = listOf(VersionedChanges(2uL, listOf(ordinary))))
        val fields = IndexedDbSensitiveFieldSupport(mapOf(1u to SimpleMarykModel), null)
        val payload = encodeChangeJournalPayload(SimpleMarykModel, 1u, fields, key.bytes, 2uL,
            encodeVersionedChange(SimpleMarykModel, versioned), listOf(ordinary, indexes))
        val update = Update.Change(SimpleMarykModel, key, 2uL, listOf(ordinary, indexes))
        val encoded = encodeCommitJournalEntry(1u, update, payload)
        val replayed = assertIs<Update.Change<*>>(decodeCommitJournalEntry(
            mapOf(1u to SimpleMarykModel), fields, createCommitJournalKey(2uL, 0u), encoded,
        ))
        assertEquals(update.changes, replayed.changes)
    }
}

private val journalListenerDispatcher = Dispatchers.Default.limitedParallelism(1)
