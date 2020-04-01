package maryk.datastore.test

import maryk.core.exceptions.RequestException
import maryk.core.properties.types.Key
import maryk.core.query.changes.Change
import maryk.core.query.changes.change
import maryk.core.query.filters.Exists
import maryk.core.query.filters.ValueIn
import maryk.core.query.pairs.with
import maryk.core.query.requests.add
import maryk.core.query.requests.change
import maryk.core.query.requests.delete
import maryk.core.query.requests.getChanges
import maryk.core.query.requests.scanChanges
import maryk.core.query.responses.statuses.AddSuccess
import maryk.core.query.responses.updates.ChangeUpdate
import maryk.core.query.responses.updates.RemovalUpdate
import maryk.datastore.shared.IsDataStore
import maryk.lib.time.DateTime
import maryk.test.assertType
import maryk.test.models.Log
import maryk.test.models.Severity.DEBUG
import maryk.test.models.Severity.ERROR
import maryk.test.models.Severity.INFO
import maryk.test.runSuspendingTest
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DataStoreScanChangesUpdateTest(
    val dataStore: IsDataStore
) : IsDataStoreTest {
    private val keys = mutableListOf<Key<Log>>()
    private var lowestVersion = ULong.MAX_VALUE

    override val allTests = mapOf(
        "failWithMutableWhereClause" to ::failWithMutableWhereClause,
        "executeScanChangesAsFlowRequest" to ::executeScanChangesAsFlowRequest
    )

    override fun initData() {
        runSuspendingTest {
            val addResponse = dataStore.execute(
                Log.add(
                    // Mind that Log stores in reverse chronological order
                    Log(message = "message 0", severity = ERROR, timestamp = DateTime(2020, 3, 28, 10, 9, 8)),
                    Log(message = "message 1", severity = INFO, timestamp = DateTime(2020, 3, 29, 12, 11, 10)),
                    Log(message = "message 2", severity = DEBUG, timestamp = DateTime(2020, 3, 30, 13, 44, 29)),
                    Log(message = "message 3", severity = DEBUG, timestamp = DateTime(2020, 3, 31, 14, 3, 48))
                )
            )
            addResponse.statuses.forEach { status ->
                val response = assertType<AddSuccess<Log>>(status)
                keys.add(response.key)
                if (response.version < lowestVersion) {
                    // Add lowest version for scan test
                    lowestVersion = response.version
                }
            }
        }
    }

    override fun resetData() {
        runSuspendingTest {
            dataStore.execute(
                Log.delete(*keys.toTypedArray(), hardDelete = true)
            )
        }
        keys.clear()
        lowestVersion = ULong.MAX_VALUE
    }

    private fun failWithMutableWhereClause() = runSuspendingTest {
        assertFailsWith<RequestException> {
            dataStore.executeFlow(
                Log.getChanges(keys[0], keys[1], where = Exists(Log { message::ref }))
            )
        }
    }

    private fun executeScanChangesAsFlowRequest() {
        updateListenerTester(
            dataStore,
            // Reverse order so keys[0], [1] and [2] are within range
            Log.scanChanges(
                startKey = keys[2],
                where = ValueIn(Log { severity::ref } with setOf(DEBUG, ERROR))
            ),
            3
        ) { responses ->
            val change1 = Change(Log { message::ref } with "new message 1")
            dataStore.execute(Log.change(
                keys[0].change(change1)
            ))

            val changeUpdate1 = responses[0].await()
            assertType<ChangeUpdate<*, *>>(changeUpdate1).apply {
                assertEquals(keys[0], key)
                assertEquals(changes, listOf(change1))
            }

            val change2 = Change(Log { message::ref } with "new message 3")
            dataStore.execute(Log.change(
                keys[2].change(change2)
            ))

            // This change should be ignored, otherwise key is wrong after changeUpdate2 check
            // This key is ignored because it is before the key at keys[2]
            dataStore.execute(Log.change(
                keys[3].change(change2)
            ))

            val changeUpdate2 = responses[1].await()
            assertType<ChangeUpdate<*, *>>(changeUpdate2).apply {
                assertEquals(keys[2], key)
                assertEquals(changes, listOf(change2))
            }

            // Is ignored since keys[1] is filtered away with where clause.
            dataStore.execute(Log.delete(keys[1]))

            dataStore.execute(Log.delete(keys[2]))

            val removalUpdate1 = responses[2].await()
            assertType<RemovalUpdate<*, *>>(removalUpdate1).apply {
                assertEquals(keys[2], key)
            }
        }
    }
}
