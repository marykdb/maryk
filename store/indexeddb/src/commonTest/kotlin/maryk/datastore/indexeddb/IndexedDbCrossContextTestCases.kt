package maryk.datastore.indexeddb

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withContext
import maryk.core.models.key
import maryk.core.query.changes.Change
import maryk.core.query.changes.Check
import maryk.core.query.changes.change
import maryk.core.query.pairs.with
import maryk.core.query.orders.Orders
import maryk.core.query.orders.ascending
import maryk.core.query.requests.add
import maryk.core.query.requests.change
import maryk.core.query.requests.delete
import maryk.core.query.requests.getUpdates
import maryk.core.query.requests.scan
import maryk.core.query.responses.statuses.AddSuccess
import maryk.core.query.responses.statuses.ChangeSuccess
import maryk.core.query.responses.statuses.DeleteSuccess
import maryk.core.query.responses.updates.AdditionUpdate
import maryk.core.query.responses.updates.ChangeUpdate
import maryk.core.query.responses.updates.OrderedKeysUpdate
import maryk.core.query.responses.updates.InitialValuesUpdate
import maryk.core.query.responses.updates.RemovalReason.HardDelete
import maryk.core.query.responses.updates.RemovalUpdate
import maryk.core.exceptions.StorageException
import maryk.datastore.test.assertStatusIs
import maryk.test.models.SimpleMarykModel
import maryk.test.models.Person
import kotlin.random.Random
import kotlin.time.Duration.Companion.milliseconds
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

private val listenerTestDispatcher = Dispatchers.Default.limitedParallelism(1)

private suspend fun Channel<Any>.receiveResponse(): Any = withContext(listenerTestDispatcher) {
    withTimeout(5_000.milliseconds) { receive() }
}

private suspend fun Channel<Any>.receiveQuietly(timeoutMillis: Long): Any? = withContext(listenerTestDispatcher) {
    withTimeoutOrNull(timeoutMillis.milliseconds) { receive() }
}

internal fun twoInstancesAllocateStrictlyIncreasingVersions() = runTest {
    installIndexedDbForTests()
    val databaseName = "maryk-indexeddb-cross-context-clock-${Random.nextInt()}"
    val first = IndexedDbDataStore.open(databaseName, mapOf(1u to SimpleMarykModel))
    val second = IndexedDbDataStore.open(databaseName, mapOf(1u to SimpleMarykModel))

    try {
        withFixedWallClockForTests(first.byteStore.currentEpochMillis().toDouble()) {
            val firstVersion = assertStatusIs<AddSuccess<SimpleMarykModel>>(
                first.execute(
                    SimpleMarykModel.add(
                        SimpleMarykModel.key(ByteArray(SimpleMarykModel.Meta.keyByteSize) { 1 }) to
                            SimpleMarykModel.create { value with "ha first context" }
                    )
                ).statuses.single()
            ).version
            val secondVersion = assertStatusIs<AddSuccess<SimpleMarykModel>>(
                second.execute(
                    SimpleMarykModel.add(
                        SimpleMarykModel.key(ByteArray(SimpleMarykModel.Meta.keyByteSize) { 2 }) to
                            SimpleMarykModel.create { value with "ha second context" }
                    )
                ).statuses.single()
            ).version

            assertTrue(secondVersion > firstVersion, "$secondVersion must be greater than $firstVersion")
        }
    } finally {
        first.close()
        second.close()
    }
}

internal fun flowObservesDurableCrossContextAddChangeDeleteOnce() = runTest {
    installIndexedDbForTests()
    val databaseName = "maryk-indexeddb-cross-context-flow-${Random.nextInt()}"
    val reader = IndexedDbDataStore.open(databaseName, mapOf(1u to SimpleMarykModel))
    val writer = IndexedDbDataStore.open(databaseName, mapOf(1u to SimpleMarykModel))
    val key = SimpleMarykModel.key(ByteArray(SimpleMarykModel.Meta.keyByteSize) { 3 })

    try {
        val responses = Channel<Any>(capacity = 5)
        val listener = launch {
            reader.executeFlow(SimpleMarykModel.getUpdates(key, filterSoftDeleted = false)).collect(responses::send)
        }
        suspend fun next() = responses.receiveResponse()
        try {
            assertIs<OrderedKeysUpdate<*>>(next())

            val initialValues = SimpleMarykModel.create { value with "ha cross-context add" }
            val add = writer.execute(SimpleMarykModel.add(key to initialValues))
            val addVersion = assertStatusIs<AddSuccess<SimpleMarykModel>>(add.statuses.single()).version
            assertIs<AdditionUpdate<SimpleMarykModel>>(next()).also { update ->
                assertEquals(key, update.key)
                assertEquals(addVersion, update.version)
                assertEquals(initialValues, update.values)
            }

            val change = Change(SimpleMarykModel { value::ref } with "ha cross-context change")
            val changeResponse = writer.execute(SimpleMarykModel.change(key.change(change)))
            val changeVersion = assertStatusIs<ChangeSuccess<SimpleMarykModel>>(changeResponse.statuses.single()).version
            assertIs<ChangeUpdate<SimpleMarykModel>>(next()).also { update ->
                assertEquals(key, update.key)
                assertEquals(changeVersion, update.version)
                assertEquals(listOf(change), update.changes)
            }

            val delete = writer.execute(SimpleMarykModel.delete(key, hardDelete = true))
            val deleteVersion = assertStatusIs<DeleteSuccess<SimpleMarykModel>>(delete.statuses.single()).version
            assertIs<RemovalUpdate<SimpleMarykModel>>(next()).also { update ->
                assertEquals(key, update.key)
                assertEquals(deleteVersion, update.version)
                assertEquals(HardDelete, update.reason)
            }

            assertEquals(null, responses.receiveQuietly(250))
        } finally {
            reader.closeAllListeners()
            listener.cancelAndJoin()
        }
    } finally {
        reader.close()
        writer.close()
    }
}

internal fun orderedFlowReplaysCrossContextIndexChange() = runTest {
    installIndexedDbForTests()
    val databaseName = "maryk-indexeddb-cross-context-index-${Random.nextInt()}"
    val reader = IndexedDbDataStore.open(databaseName, mapOf(1u to Person))
    val writer = IndexedDbDataStore.open(databaseName, mapOf(1u to Person))
    val firstKey = Person.key(ByteArray(Person.Meta.keyByteSize) { 4 })
    val secondKey = Person.key(ByteArray(Person.Meta.keyByteSize) { 5 })

    try {
        writer.execute(
            Person.add(
                firstKey to Person.create { firstName with "A"; surname with "Same" },
                secondKey to Person.create { firstName with "B"; surname with "Same" },
            )
        )
        val responses = Channel<Any>(capacity = 3)
        val listener = launch {
            reader.executeFlow(
                Person.scan(
                    order = Orders(Person { surname::ref }.ascending(), Person { firstName::ref }.ascending())
                )
            ).collect(responses::send)
        }
        suspend fun next() = responses.receiveResponse()
        try {
            assertIs<InitialValuesUpdate<*>>(next()).also { initial ->
                assertEquals(listOf(firstKey, secondKey), initial.values.map { it.key })
            }
            writer.execute(Person.change(firstKey.change(Change(Person { firstName::ref } with "Z"))))
            assertIs<ChangeUpdate<Person>>(next()).also { update ->
                assertEquals(firstKey, update.key)
                assertEquals(1, update.index)
            }
            assertEquals(null, responses.receiveQuietly(250))
        } finally {
            reader.closeAllListeners()
            listener.cancelAndJoin()
        }
    } finally {
        reader.close()
        writer.close()
    }
}

internal fun pollingDeliversWithoutBroadcastChannel() = runTest {
    withoutBroadcastChannelForTests {
        installIndexedDbForTests()
        val databaseName = "maryk-indexeddb-polling-${Random.nextInt()}"
        val reader = IndexedDbDataStore.open(databaseName, mapOf(1u to SimpleMarykModel))
        val writer = IndexedDbDataStore.open(databaseName, mapOf(1u to SimpleMarykModel))
        val key = SimpleMarykModel.key(ByteArray(SimpleMarykModel.Meta.keyByteSize) { 6 })
        val responses = Channel<Any>(capacity = 3)
        val listener = launch {
            reader.executeFlow(SimpleMarykModel.getUpdates(key)).collect(responses::send)
        }
        try {
            responses.receiveResponse()
            writer.execute(
                SimpleMarykModel.add(key to SimpleMarykModel.create { value with "ha polling" })
            )
            assertIs<AdditionUpdate<*>>(responses.receiveResponse())
            assertEquals(null, responses.receiveQuietly(500))
        } finally {
            reader.closeAllListeners()
            listener.cancelAndJoin()
            reader.close()
            writer.close()
        }
    }
}

internal fun localCommitCannotSkipPendingExternalCommit() = runTest {
    installIndexedDbForTests()
    withoutBroadcastChannelForTests {
        val databaseName = "maryk-indexeddb-cursor-race-${Random.nextInt()}"
        val readerAndWriter = IndexedDbDataStore.open(databaseName, mapOf(1u to SimpleMarykModel))
        val externalWriter = IndexedDbDataStore.open(databaseName, mapOf(1u to SimpleMarykModel))
        val externalKey = SimpleMarykModel.key(ByteArray(SimpleMarykModel.Meta.keyByteSize) { 7 })
        val localKey = SimpleMarykModel.key(ByteArray(SimpleMarykModel.Meta.keyByteSize) { 8 })
        val responses = Channel<Any>(capacity = 4)
        val listener = launch {
            readerAndWriter.executeFlow(SimpleMarykModel.getUpdates(externalKey, localKey)).collect(responses::send)
        }
        try {
            responses.receiveResponse()
            externalWriter.execute(
                SimpleMarykModel.add(externalKey to SimpleMarykModel.create { value with "ha external" })
            )
            readerAndWriter.execute(
                SimpleMarykModel.add(localKey to SimpleMarykModel.create { value with "ha local" })
            )
            assertEquals(
                listOf(externalKey, localKey),
                listOf(
                    assertIs<AdditionUpdate<SimpleMarykModel>>(responses.receiveResponse()).key,
                    assertIs<AdditionUpdate<SimpleMarykModel>>(responses.receiveResponse()).key,
                )
            )
            assertEquals(null, responses.receiveQuietly(500))
        } finally {
            readerAndWriter.closeAllListeners()
            listener.cancelAndJoin()
            readerAndWriter.close()
            externalWriter.close()
        }
    }
}

internal fun checkOnlyChangeRemainsSuccessful() = runTest {
    installIndexedDbForTests()
    val databaseName = "maryk-indexeddb-check-only-${Random.nextInt()}"
    val dataStore = IndexedDbDataStore.open(databaseName, mapOf(1u to SimpleMarykModel))
    val key = SimpleMarykModel.key(ByteArray(SimpleMarykModel.Meta.keyByteSize) { 41 })

    try {
        val addVersion = assertStatusIs<AddSuccess<SimpleMarykModel>>(
            dataStore.execute(
                SimpleMarykModel.add(key to SimpleMarykModel.create { value with "ha check-only" })
            ).statuses.single()
        ).version

        assertStatusIs<ChangeSuccess<SimpleMarykModel>>(
            dataStore.execute(
                SimpleMarykModel.change(
                    key.change(
                        Check(SimpleMarykModel { value::ref } with "ha check-only"),
                        lastVersion = addVersion,
                    )
                )
            ).statuses.single()
        )
    } finally {
        dataStore.close()
    }
}

internal fun reopenStartsAtPersistedJournalFloor() = runTest {
    installIndexedDbForTests()
    val databaseName = "maryk-indexeddb-journal-floor-${Random.nextInt()}"
    val key = SimpleMarykModel.key(ByteArray(SimpleMarykModel.Meta.keyByteSize) { 43 })
    val first = IndexedDbDataStore.open(databaseName, mapOf(1u to SimpleMarykModel))

    try {
        first.execute(
            SimpleMarykModel.add(key to SimpleMarykModel.create { value with "ha retained floor" })
        )
        assertTrue(first.byteStore.scan(CommitJournalStoreName).isEmpty())
        assertTrue(first.byteStore.get("meta", CommitJournalFloorMetadataKey) != null)
    } finally {
        first.close()
    }

    val reopened = IndexedDbDataStore.open(databaseName, mapOf(1u to SimpleMarykModel))
    try {
        assertTrue(runCatching { reopened.execute(SimpleMarykModel.getUpdates(key)) }.isSuccess)
    } finally {
        reopened.close()
    }
}

internal fun retentionGapFailsListenerExplicitly() = runTest {
    installIndexedDbForTests()
    withoutBroadcastChannelForTests {
        val originalLimit = indexedDbMaxRetainedJournalEntries
        indexedDbMaxRetainedJournalEntries = 2
        indexedDbJournalPollingPausedForTests = true
        val databaseName = "maryk-indexeddb-journal-gap-${Random.nextInt()}"
        val reader = IndexedDbDataStore.open(databaseName, mapOf(1u to SimpleMarykModel))
        val writer = IndexedDbDataStore.open(databaseName, mapOf(1u to SimpleMarykModel))
        val observedKey = SimpleMarykModel.key(ByteArray(SimpleMarykModel.Meta.keyByteSize) { 42 })
        val listenerStopped = CompletableDeferred<Unit>()
        val initial = CompletableDeferred<Unit>()
        val listener = launch {
            try {
                reader.executeFlow(SimpleMarykModel.getUpdates(observedKey)).collect { initial.complete(Unit) }
            } catch (_: StorageException) {
                // Retention gap explicitly requires the collector to resubscribe for a fresh snapshot.
            } finally {
                listenerStopped.complete(Unit)
            }
        }
        try {
            withContext(listenerTestDispatcher) { withTimeout(5_000.milliseconds) { initial.await() } }
            repeat(4) { index ->
                val key = SimpleMarykModel.key(ByteArray(SimpleMarykModel.Meta.keyByteSize) { (index + 9).toByte() })
                writer.execute(SimpleMarykModel.add(key to SimpleMarykModel.create { value with "ha $index" }))
            }
            assertTrue(writer.byteStore.scan(CommitJournalStoreName).size <= 2)
            assertTrue(writer.byteStore.get("meta", CommitJournalFloorMetadataKey) != null)
            indexedDbJournalPollingPausedForTests = false
            assertIs<StorageException>(runCatching { reader.execute(SimpleMarykModel.getUpdates(observedKey)) }.exceptionOrNull())
            withContext(listenerTestDispatcher) { withTimeout(5_000.milliseconds) { listenerStopped.await() } }
        } finally {
            indexedDbJournalPollingPausedForTests = false
            indexedDbMaxRetainedJournalEntries = originalLimit
            reader.closeAllListeners()
            listener.cancelAndJoin()
            reader.close()
            writer.close()
        }
    }
}

internal fun webLockAndFallbackLeaseShareOneFence() = runTest {
    installIndexedDbForTests()
    if (!webLocksAvailableForTests()) return@runTest

    val databaseName = "maryk-indexeddb-mixed-lock-${Random.nextInt()}"
    val webLockStore = openIndexedDbByteStore(databaseName, setOf("rows"))
    val fallbackStore = openIndexedDbByteStore(databaseName, setOf("rows"))
    val webLockEntered = CompletableDeferred<Unit>()
    val releaseWebLock = CompletableDeferred<Unit>()
    val events = mutableListOf<String>()

    try {
        val webLockWrite = async {
            webLockStore.transaction(setOf("rows"), IndexedDbTransactionMode.READWRITE) {
                events += "web-lock-start"
                webLockEntered.complete(Unit)
                releaseWebLock.await()
                events += "web-lock-end"
            }
        }
        webLockEntered.await()

        val fallbackWrite = async {
            withoutWebLocks {
                fallbackStore.transaction(setOf("rows"), IndexedDbTransactionMode.READWRITE) {
                    events += "fallback"
                }
            }
        }
        delay(50)
        assertEquals(listOf("web-lock-start"), events)

        releaseWebLock.complete(Unit)
        webLockWrite.await()
        fallbackWrite.await()
        assertEquals(listOf("web-lock-start", "web-lock-end", "fallback"), events)
    } finally {
        releaseWebLock.complete(Unit)
        webLockStore.close()
        fallbackStore.close()
    }
}
