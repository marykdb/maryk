package maryk.datastore.test

import maryk.core.properties.types.Key
import maryk.core.query.changes.Change
import maryk.core.query.changes.change
import maryk.core.query.filters.Equals
import maryk.core.query.filters.Not
import maryk.core.query.pairs.with
import maryk.core.query.requests.add
import maryk.core.query.requests.change
import maryk.core.query.requests.delete
import maryk.core.query.requests.get
import maryk.core.query.requests.getChanges
import maryk.core.query.requests.getUpdates
import maryk.core.query.responses.FetchByKey
import maryk.core.query.responses.statuses.AddSuccess
import maryk.core.query.responses.statuses.DeleteSuccess
import maryk.core.query.responses.updates.AdditionUpdate
import maryk.core.query.responses.updates.ChangeUpdate
import maryk.core.query.responses.updates.InitialChangesUpdate
import maryk.core.query.responses.updates.InitialValuesUpdate
import maryk.core.query.responses.updates.OrderedKeysUpdate
import maryk.core.query.responses.updates.RemovalReason.NotInRange
import maryk.core.query.responses.updates.RemovalReason.SoftDelete
import maryk.core.query.responses.updates.RemovalUpdate
import maryk.datastore.shared.IsDataStore
import maryk.test.models.SimpleMarykModel
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.expect

class DataStoreGetUpdatesAndFlowTest(
    val dataStore: IsDataStore
) : IsDataStoreTest {
    private val testKeys = mutableListOf<Key<SimpleMarykModel>>()
    private var lowestVersion = ULong.MAX_VALUE
    private var highestInitVersion = ULong.MIN_VALUE

    override val allTests = mapOf(
        "executeSimpleGetUpdatesRequest" to ::executeSimpleGetUpdatesRequest,
        "executeGetValuesAsFlowRequest" to ::executeGetValuesAsFlowRequest,
        "executeGetChangesAsFlowRequest" to ::executeGetChangesAsFlowRequest,
        "executeGetUpdatesAsFlowRequest" to ::executeGetUpdatesAsFlowRequest,
        "executeGetUpdatesAsFlowWithMutableWhereRequest" to ::executeGetUpdatesAsFlowWithMutableWhereRequest,
        "executeGetUpdatesWithInitChangesAsFlowRequest" to ::executeGetUpdatesWithInitChangesAsFlowRequest
    )

    override suspend fun initData() {
        val addResponse = dataStore.execute(
            SimpleMarykModel.add(
                SimpleMarykModel.create { value with "haha1" },
                SimpleMarykModel.create { value with "haha2" },
                SimpleMarykModel.create { value with "haha3" },
            )
        )
        addResponse.statuses.forEach { status ->
            val response = assertStatusIs<AddSuccess<SimpleMarykModel>>(status)
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
            SimpleMarykModel.delete(*testKeys.toTypedArray(), hardDelete = true)
        ).statuses.forEach {
            assertStatusIs<DeleteSuccess<*>>(it)
        }
        testKeys.clear()
        lowestVersion = ULong.MAX_VALUE
        highestInitVersion = ULong.MIN_VALUE
    }

    private suspend fun executeSimpleGetUpdatesRequest() {
        val getResponse = dataStore.execute(
            SimpleMarykModel.getUpdates(testKeys[0], testKeys[1])
        )

        expect(3) { getResponse.updates.size }
        expect(FetchByKey) {getResponse.dataFetchType}

        assertIs<OrderedKeysUpdate<*>>(getResponse.updates[0]).apply {
            assertEquals(listOf(testKeys[0], testKeys[1]), keys)
            assertNull(sortingKeys)
            assertEquals(highestInitVersion, version)
        }

        assertIs<AdditionUpdate<SimpleMarykModel>>(getResponse.updates[1]).apply {
            assertEquals(testKeys[0], key)
            assertEquals(SimpleMarykModel.create { value with "haha1" }, values)
        }

        assertIs<AdditionUpdate<SimpleMarykModel>>(getResponse.updates[2]).apply {
            assertEquals(testKeys[1], key)
            assertEquals(SimpleMarykModel.create { value with "haha2" }, values)
        }
    }

    private suspend fun executeGetValuesAsFlowRequest() = updateListenerTester(
        dataStore,
        SimpleMarykModel.get(testKeys[0], testKeys[1]),
        2
    ) { responses ->
        assertIs<InitialValuesUpdate<*>>(responses[0].await()).apply {
            assertEquals(listOf(testKeys[0], testKeys[1]), values.map { it.key })
            assertEquals(highestInitVersion, version)
        }

        val change1 = Change(SimpleMarykModel { value::ref } with "haha 55")
        dataStore.execute(SimpleMarykModel.change(
            testKeys[0].change(change1)
        ))

        val changeUpdate1 = responses[1].await()
        assertIs<ChangeUpdate<*>>(changeUpdate1).apply {
            assertEquals(testKeys[0], key)
            assertEquals(listOf(change1), changes)
        }
    }

    private suspend fun executeGetChangesAsFlowRequest() = updateListenerTester(
        dataStore,
        SimpleMarykModel.getChanges(testKeys[0], testKeys[1]),
        2
    ) { responses ->
        assertIs<InitialChangesUpdate<*>>(responses[0].await()).apply {
            assertEquals(listOf(testKeys[0], testKeys[1]), changes.map { it.key })
            assertEquals(highestInitVersion, version)
        }

        val change1 = Change(SimpleMarykModel { value::ref } with "haha 66")
        dataStore.execute(SimpleMarykModel.change(
            testKeys[0].change(change1)
        ))

        val changeUpdate1 = responses[1].await()
        assertIs<ChangeUpdate<*>>(changeUpdate1).apply {
            assertEquals(testKeys[0], key)
            assertEquals(listOf(change1), changes)
        }
    }

    private suspend fun executeGetUpdatesAsFlowRequest() = updateListenerTester(
        dataStore,
        SimpleMarykModel.getUpdates(testKeys[0], testKeys[1], fromVersion = highestInitVersion + 1uL),
        4
    ) { responses ->
        assertIs<OrderedKeysUpdate<*>>(responses[0].await()).apply {
            assertEquals(listOf(testKeys[0], testKeys[1]), keys)
            assertEquals(highestInitVersion, version)
        }

        val change1 = Change(SimpleMarykModel { value::ref } with "haha5")
        dataStore.execute(SimpleMarykModel.change(
            testKeys[0].change(change1)
        ))

        val changeUpdate1 = responses[1].await()
        assertIs<ChangeUpdate<*>>(changeUpdate1).apply {
            assertEquals(testKeys[0], key)
            assertEquals(listOf(change1), changes)
        }

        val change2 = Change(SimpleMarykModel { value::ref } with "haha6")
        dataStore.execute(SimpleMarykModel.change(
            testKeys[1].change(change2)
        ))

        // This change should be ignored, otherwise key is wrong after changeUpdate2 check
        dataStore.execute(SimpleMarykModel.change(
            testKeys[2].change(change2)
        ))

        val changeUpdate2 = responses[2].await()
        assertIs<ChangeUpdate<*>>(changeUpdate2).apply {
            assertEquals(testKeys[1], key)
            assertEquals(listOf(change2), changes)
        }

        dataStore.execute(SimpleMarykModel.delete(testKeys[1]))

        val removalUpdate1 = responses[3].await()
        assertIs<RemovalUpdate<*>>(removalUpdate1).apply {
            assertEquals(testKeys[1], key)
            assertEquals(SoftDelete, reason)
        }
    }

    private suspend fun executeGetUpdatesAsFlowWithMutableWhereRequest() = updateListenerTester(
        dataStore,
        SimpleMarykModel.getUpdates(
            testKeys[0],
            testKeys[1],
            where = Not(Equals(SimpleMarykModel { value::ref } with "haha0")),
            fromVersion = highestInitVersion + 1uL
        ),
        3
    ) { responses ->
        assertIs<OrderedKeysUpdate<*>>(responses[0].await()).apply {
            assertEquals(listOf(testKeys[0], testKeys[1]), keys)
            assertEquals(highestInitVersion, version)
        }

        val change1 = Change(SimpleMarykModel { value::ref } with "haha5")
        dataStore.execute(SimpleMarykModel.change(
            testKeys[0].change(change1)
        ))

        val changeUpdate1 = responses[1].await()
        assertIs<ChangeUpdate<*>>(changeUpdate1).apply {
            assertEquals(testKeys[0], key)
            assertEquals(listOf(change1), changes)
        }

        val change2 = Change(SimpleMarykModel { value::ref } with "haha0")
        dataStore.execute(SimpleMarykModel.change(
            testKeys[1].change(change2)
        ))

        val changeUpdate2 = responses[2].await()
        assertIs<RemovalUpdate<*>>(changeUpdate2).apply {
            assertEquals(testKeys[1], key)
            assertEquals(NotInRange, reason)
        }
    }

    private suspend fun executeGetUpdatesWithInitChangesAsFlowRequest() = updateListenerTester(
        dataStore,
        SimpleMarykModel.getUpdates(testKeys[0], testKeys[2]),
        4
    ) { responses ->
        assertIs<OrderedKeysUpdate<*>>(responses[0].await()).apply {
            assertEquals(listOf(testKeys[0], testKeys[2]), keys)
            assertEquals(highestInitVersion, version)
        }

        assertIs<AdditionUpdate<SimpleMarykModel>>(responses[1].await()).apply {
            assertEquals(testKeys[0], key)
            assertEquals(lowestVersion, version)
            assertEquals(
                SimpleMarykModel.create {
                    value with "haha1"
                },
                values
            )
        }
        assertIs<AdditionUpdate<SimpleMarykModel>>(responses[2].await()).apply {
            assertEquals(testKeys[2], key)
            assertEquals(highestInitVersion, version)
            assertEquals(
                SimpleMarykModel.create {
                    value with "haha3"
                },
                values
            )
        }

        val change1 = Change(SimpleMarykModel { value::ref } with "haha5")
        dataStore.execute(SimpleMarykModel.change(
            testKeys[0].change(change1)
        ))

        val changeUpdate1 = responses[3].await()
        assertIs<ChangeUpdate<*>>(changeUpdate1).apply {
            assertEquals(testKeys[0], key)
            assertEquals(listOf(change1), changes)
        }
    }
}
