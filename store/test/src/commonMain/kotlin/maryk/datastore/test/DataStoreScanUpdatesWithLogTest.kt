package maryk.datastore.test

import kotlinx.datetime.LocalDateTime
import maryk.core.properties.types.Key
import maryk.core.query.changes.Change
import maryk.core.query.changes.change
import maryk.core.query.filters.Equals
import maryk.core.query.filters.Not
import maryk.core.query.filters.ValueIn
import maryk.core.query.pairs.with
import maryk.core.query.requests.add
import maryk.core.query.requests.change
import maryk.core.query.requests.delete
import maryk.core.query.requests.scanUpdates
import maryk.core.query.responses.statuses.AddSuccess
import maryk.core.query.responses.updates.ChangeUpdate
import maryk.core.query.responses.updates.OrderedKeysUpdate
import maryk.core.query.responses.updates.RemovalReason.NotInRange
import maryk.core.query.responses.updates.RemovalReason.SoftDelete
import maryk.core.query.responses.updates.RemovalUpdate
import maryk.datastore.shared.IsDataStore
import maryk.test.models.Log
import maryk.test.models.Severity.DEBUG
import maryk.test.models.Severity.ERROR
import maryk.test.models.Severity.INFO
import kotlin.test.assertEquals
import kotlin.test.assertIs

class DataStoreScanUpdatesWithLogTest(
    val dataStore: IsDataStore
) : IsDataStoreTest {
    private val testKeys = mutableListOf<Key<Log>>()
    private var lowestVersion = ULong.MAX_VALUE
    private var highestInitVersion = ULong.MIN_VALUE

    override val allTests = mapOf(
        "executeScanUpdatesAsFlowRequest" to ::executeScanUpdatesAsFlowRequest,
        "executeScanUpdatesAsFlowRequestWithMutableWhere" to ::executeScanUpdatesAsFlowRequestWithMutableWhere
    )

    override suspend fun initData() {
        val addResponse = dataStore.execute(
            Log.add(
                // Mind that Log stores in reverse chronological order
                Log(message = "message 0", severity = ERROR, timestamp = LocalDateTime(2020, 3, 28, 10, 9, 8)),
                Log(message = "message 1", severity = INFO, timestamp = LocalDateTime(2020, 3, 29, 12, 11, 10)),
                Log(message = "message 2", severity = DEBUG, timestamp = LocalDateTime(2020, 3, 30, 13, 44, 29)),
                Log(message = "message 3", severity = DEBUG, timestamp = LocalDateTime(2020, 3, 31, 14, 3, 48))
            )
        )
        addResponse.statuses.forEach { status ->
            val response = assertStatusIs<AddSuccess<Log>>(status)
            testKeys.add(response.key)
            if (response.version < lowestVersion) {
                // Add lowest version for scan test
                lowestVersion = response.version
            }
            if (response.version > highestInitVersion) {
                highestInitVersion = response.version
            }
        }
    }

    override suspend fun resetData() {
        dataStore.execute(
            Log.delete(*testKeys.toTypedArray(), hardDelete = true)
        )
        testKeys.clear()
        lowestVersion = ULong.MAX_VALUE
        highestInitVersion = ULong.MIN_VALUE
    }

    private suspend fun executeScanUpdatesAsFlowRequestWithMutableWhere() {
        updateListenerTester(
            dataStore,
            Log.scanUpdates(
                testKeys[2],
                where = Not(Equals(Log { message::ref } with "new message 0")),
                fromVersion = highestInitVersion + 1uL
            ),
            3
        ) { responses ->
            assertIs<OrderedKeysUpdate<*>>(responses[0].await()).apply {
                assertEquals(listOf(testKeys[2], testKeys[1], testKeys[0]), keys)
                assertEquals(highestInitVersion, version)
            }

            val change1 = Change(Log { message::ref } with "new message 5")
            dataStore.execute(Log.change(
                testKeys[0].change(change1)
            ))

            assertIs<ChangeUpdate<*>>(responses[1].await()).apply {
                assertEquals(testKeys[0], key)
                assertEquals(listOf(change1), changes)
            }

            val change2 = Change(Log { message::ref } with "new message 0")
            dataStore.execute(Log.change(
                testKeys[2].change(change2)
            ))

            assertIs<RemovalUpdate<*>>(responses[2].await()).apply {
                assertEquals(testKeys[2], key)
                assertEquals(NotInRange, reason)
            }
        }
    }

    private suspend fun executeScanUpdatesAsFlowRequest() {
        updateListenerTester(
            dataStore,
            // Reverse order so keys[0], [1] and [2] are within range
            Log.scanUpdates(
                startKey = testKeys[2],
                where = ValueIn(Log { severity::ref } with setOf(DEBUG, ERROR)),
                fromVersion = highestInitVersion + 1uL
            ),
            4
        ) { responses ->
            assertIs<OrderedKeysUpdate<*>>(responses[0].await()).apply {
                assertEquals(listOf(testKeys[2], testKeys[0]), keys)
                assertEquals(highestInitVersion, version)
            }

            val change1 = Change(Log { message::ref } with "new message 1")
            dataStore.execute(Log.change(
                testKeys[0].change(change1)
            ))

            val changeUpdate1 = responses[1].await()
            assertIs<ChangeUpdate<*>>(changeUpdate1).apply {
                assertEquals(testKeys[0], key)
                assertEquals(listOf(change1), changes)
            }

            val change2 = Change(Log { message::ref } with "new message 3")
            dataStore.execute(Log.change(
                testKeys[2].change(change2)
            ))

            // This change should be ignored, otherwise key is wrong after changeUpdate2 check
            // This key is ignored because it is before the key at keys[2]
            dataStore.execute(Log.change(
                testKeys[3].change(change2)
            ))

            val changeUpdate2 = responses[2].await()
            assertIs<ChangeUpdate<*>>(changeUpdate2).apply {
                assertEquals(testKeys[2], key)
                assertEquals(listOf(change2), changes)
            }

            // Is ignored since keys[1] is filtered away with where clause.
            dataStore.execute(Log.delete(testKeys[1]))

            dataStore.execute(Log.delete(testKeys[2]))

            val removalUpdate1 = responses[3].await()
            assertIs<RemovalUpdate<*>>(removalUpdate1).apply {
                assertEquals(testKeys[2], key)
                assertEquals(SoftDelete, reason)
            }
        }
    }
}
