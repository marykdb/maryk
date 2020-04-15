package maryk.datastore.test

import maryk.core.exceptions.RequestException
import maryk.core.properties.types.Key
import maryk.core.query.changes.Change
import maryk.core.query.changes.change
import maryk.core.query.filters.Exists
import maryk.core.query.pairs.with
import maryk.core.query.requests.add
import maryk.core.query.requests.change
import maryk.core.query.requests.delete
import maryk.core.query.requests.getChanges
import maryk.core.query.responses.statuses.AddSuccess
import maryk.core.query.responses.updates.AdditionUpdate
import maryk.core.query.responses.updates.ChangeUpdate
import maryk.core.query.responses.updates.RemovalReason.SoftDelete
import maryk.core.query.responses.updates.RemovalUpdate
import maryk.core.values.Values
import maryk.datastore.shared.IsDataStore
import maryk.test.assertType
import maryk.test.models.SimpleMarykModel
import maryk.test.models.SimpleMarykModel.Properties
import maryk.test.runSuspendingTest
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DataStoreGetChangesUpdateTest(
    val dataStore: IsDataStore
) : IsDataStoreTest {
    private val keys = mutableListOf<Key<SimpleMarykModel>>()
    private var lowestVersion = ULong.MAX_VALUE
    private var highestInitVersion = ULong.MIN_VALUE

    override val allTests = mapOf(
        "failWithMutableWhereClause" to ::failWithMutableWhereClause,
        "executeGetChangesAsFlowRequest" to ::executeGetChangesAsFlowRequest,
        "executeGetChangesWithInitChangesAsFlowRequest" to ::executeGetChangesWithInitChangesAsFlowRequest
    )

    override fun initData() {
        runSuspendingTest {
            val addResponse = dataStore.execute(
                SimpleMarykModel.add(
                    SimpleMarykModel(value = "haha1"),
                    SimpleMarykModel(value = "haha2"),
                    SimpleMarykModel(value = "haha3")
                )
            )
            addResponse.statuses.forEach { status ->
                val response = assertType<AddSuccess<SimpleMarykModel>>(status)
                keys.add(response.key)
                if (response.version < lowestVersion) {
                    // Add lowest version for scan test
                    lowestVersion = response.version
                }
                if (response.version > highestInitVersion) {
                    highestInitVersion = response.version
                }
            }
        }
    }

    override fun resetData() {
        runSuspendingTest {
            dataStore.execute(
                SimpleMarykModel.delete(*keys.toTypedArray(), hardDelete = true)
            )
        }
        keys.clear()
        lowestVersion = ULong.MAX_VALUE
        highestInitVersion = ULong.MIN_VALUE
    }

    private fun failWithMutableWhereClause() = runSuspendingTest {
        assertFailsWith<RequestException> {
            dataStore.executeFlow(
                SimpleMarykModel.getChanges(keys[0], keys[1], where = Exists(SimpleMarykModel { value::ref }))
            )
        }
    }

    private fun executeGetChangesAsFlowRequest() = updateListenerTester(
        dataStore,
        SimpleMarykModel.getChanges(keys[0], keys[1], fromVersion = highestInitVersion + 1uL),
        3
    ) { responses ->
        val change1 = Change(SimpleMarykModel { value::ref } with "haha5")
        dataStore.execute(SimpleMarykModel.change(
            keys[0].change(change1)
        ))

        val changeUpdate1 = responses[0].await()
        assertType<ChangeUpdate<*, *>>(changeUpdate1).apply {
            assertEquals(keys[0], key)
            assertEquals(listOf(change1), changes)
        }

        val change2 = Change(SimpleMarykModel { value::ref } with "haha6")
        dataStore.execute(SimpleMarykModel.change(
            keys[1].change(change2)
        ))

        // This change should be ignored, otherwise key is wrong after changeUpdate2 check
        dataStore.execute(SimpleMarykModel.change(
            keys[2].change(change2)
        ))

        val changeUpdate2 = responses[1].await()
        assertType<ChangeUpdate<*, *>>(changeUpdate2).apply {
            assertEquals(keys[1], key)
            assertEquals(listOf(change2), changes)
        }

        dataStore.execute(SimpleMarykModel.delete(keys[1]))

        val removalUpdate1 = responses[2].await()
        assertType<RemovalUpdate<*, *>>(removalUpdate1).apply {
            assertEquals(keys[1], key)
            assertEquals(SoftDelete, reason)
        }
    }

    private fun executeGetChangesWithInitChangesAsFlowRequest() = updateListenerTester(
        dataStore,
        SimpleMarykModel.getChanges(keys[0], keys[2]),
        3
    ) { responses ->
        assertType<AdditionUpdate<SimpleMarykModel, Properties>>(responses[0].await()).apply {
            assertEquals(keys[0], key)
            assertEquals(lowestVersion, version)
            assertEquals(
                SimpleMarykModel(value = "haha1"),
                values
            )
        }
        assertType<AdditionUpdate<SimpleMarykModel, Properties>>(responses[1].await()).apply {
            assertEquals(keys[2], key)
            assertEquals(highestInitVersion, version)
            assertEquals(
                SimpleMarykModel(value = "haha3"),
                values
            )
        }

        val change1 = Change(SimpleMarykModel { value::ref } with "haha5")
        dataStore.execute(SimpleMarykModel.change(
            keys[0].change(change1)
        ))

        val changeUpdate1 = responses[2].await()
        assertType<ChangeUpdate<*, *>>(changeUpdate1).apply {
            assertEquals(keys[0], key)
            assertEquals(listOf(change1), changes)
        }
    }
}
