package maryk.datastore.test

import maryk.core.processors.datastore.IsDataStore
import maryk.core.properties.types.Key
import maryk.core.query.changes.ObjectSoftDeleteChange
import maryk.core.query.requests.delete
import maryk.core.query.requests.get
import maryk.core.query.requests.getChanges
import maryk.core.query.responses.statuses.AddSuccess
import maryk.core.query.responses.statuses.DeleteSuccess
import maryk.core.query.responses.statuses.StatusType.DELETE_SUCCESS
import maryk.test.assertType
import maryk.test.models.SimpleMarykModel
import maryk.test.requests.addRequest
import maryk.test.runSuspendingTest
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.expect
import kotlin.test.fail

class DataStoreDeleteTest(
    val dataStore: IsDataStore
) : IsDataStoreTest {
    private val keys = mutableListOf<Key<SimpleMarykModel>>()

    override val allTests = mapOf(
        "executeDeleteRequest" to ::executeDeleteRequest,
        "processHardDeleteRequest" to ::processHardDeleteRequest
    )

    override fun initData() {
        runSuspendingTest {
            val addResponse = dataStore.execute(
                addRequest
            )
            addResponse.statuses.forEach { status ->
                val response = assertType<AddSuccess<SimpleMarykModel>>(status)
                keys.add(response.key)
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
    }

    private fun executeDeleteRequest() = runSuspendingTest {
        val deleteResponse = dataStore.execute(
            SimpleMarykModel.delete(
                keys[0]
            )
        )

        expect(1) { deleteResponse.statuses.size }
        expect(DELETE_SUCCESS) { deleteResponse.statuses[0].statusType }
        with(deleteResponse.statuses[0]) {
            expect(DELETE_SUCCESS) { statusType }
            assertType<DeleteSuccess<SimpleMarykModel>>(this)
        }

        val getResponse = dataStore.execute(
            addRequest.dataModel.get(keys[0])
        )
        assertTrue { getResponse.values.isEmpty() }

        val getResponseWithDeleted = dataStore.execute(
            addRequest.dataModel.get(keys[0], filterSoftDeleted = false)
        )
        assertFalse { getResponseWithDeleted.values.isEmpty() }
        assertTrue { getResponseWithDeleted.values[0].isDeleted }

        val getChangesResponse = dataStore.execute(
            addRequest.dataModel.getChanges(keys[0])
        )

        assertTrue { getChangesResponse.changes.isEmpty() }

        val getChangesWithDeletedResponse = dataStore.execute(
            addRequest.dataModel.getChanges(keys[0], filterSoftDeleted = false)
        )

        expect(1) { getChangesWithDeletedResponse.changes.size }

        // Timing is vast so sometimes creation and deletion are combined into one change. Catch both
        when (getChangesWithDeletedResponse.changes.first().changes.size) {
            1 -> {
                getChangesWithDeletedResponse.changes.first().changes.first().let {
                    expect(2) { it.changes.size }
                    expect(ObjectSoftDeleteChange(true)) { it.changes.first() }
                }
            }
            2 -> {
                getChangesWithDeletedResponse.changes[0].changes.last().let {
                    expect(1) { it.changes.size }
                    expect(ObjectSoftDeleteChange(true)) { it.changes.first() }
                }
            }
            else -> fail("Unexpected size")
        }
    }

    private fun processHardDeleteRequest() = runSuspendingTest {
        val deleteResponse = dataStore.execute(
            SimpleMarykModel.delete(
                keys[1],
                hardDelete = true
            )
        )

        expect(1) { deleteResponse.statuses.size }
        expect(DELETE_SUCCESS) { deleteResponse.statuses[0].statusType }
        with(deleteResponse.statuses[0]) {
            assertType<DeleteSuccess<SimpleMarykModel>>(this)
        }

        val getResponse = dataStore.execute(
            addRequest.dataModel.get(keys[1])
        )
        assertTrue { getResponse.values.isEmpty() }
    }
}
