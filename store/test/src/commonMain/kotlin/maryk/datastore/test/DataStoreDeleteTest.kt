package maryk.datastore.test

import maryk.core.models.PropertyBaseRootDataModel
import maryk.core.properties.types.Key
import maryk.core.query.changes.ObjectSoftDeleteChange
import maryk.core.query.requests.delete
import maryk.core.query.requests.get
import maryk.core.query.requests.getChanges
import maryk.core.query.responses.statuses.AddSuccess
import maryk.core.query.responses.statuses.DeleteSuccess
import maryk.core.query.responses.statuses.StatusType.DELETE_SUCCESS
import maryk.datastore.shared.IsDataStore
import maryk.test.models.SimpleMarykModel
import maryk.test.requests.addRequest
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.test.expect

class DataStoreDeleteTest(
    val dataStore: IsDataStore
) : IsDataStoreTest {
    private val keys = mutableListOf<Key<PropertyBaseRootDataModel<SimpleMarykModel>>>()

    override val allTests = mapOf(
        "executeDeleteRequest" to ::executeDeleteRequest,
        "processHardDeleteRequest" to ::processHardDeleteRequest
    )

    override suspend fun initData() {
        val addResponse = dataStore.execute(
            addRequest
        )
        addResponse.statuses.forEach { status ->
            val response = assertIs<AddSuccess<PropertyBaseRootDataModel<SimpleMarykModel>>>(status)
            keys.add(response.key)
        }
    }

    override suspend fun resetData() {
        dataStore.execute(
            SimpleMarykModel.Model.delete(*keys.toTypedArray(), hardDelete = true)
        )
        keys.clear()
    }

    private suspend fun executeDeleteRequest() {
        val deleteResponse = dataStore.execute(
            SimpleMarykModel.Model.delete(
                keys[0]
            )
        )

        expect(1) { deleteResponse.statuses.size }
        expect(DELETE_SUCCESS) { deleteResponse.statuses[0].statusType }
        with(deleteResponse.statuses[0]) {
            expect(DELETE_SUCCESS) { statusType }
            assertIs<DeleteSuccess<PropertyBaseRootDataModel<SimpleMarykModel>>>(this)
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

        val firstChanges = getChangesWithDeletedResponse.changes.first().changes
        assertEquals(2, firstChanges.size)

        firstChanges.last().let {
            expect(1) { it.changes.size }
            expect(ObjectSoftDeleteChange(true)) { it.changes.first() }
        }
    }

    private suspend fun processHardDeleteRequest() {
        val deleteResponse = dataStore.execute(
            SimpleMarykModel.Model.delete(
                keys[1],
                hardDelete = true
            )
        )

        expect(1) { deleteResponse.statuses.size }
        expect(DELETE_SUCCESS) { deleteResponse.statuses[0].statusType }
        with(deleteResponse.statuses[0]) {
            assertIs<DeleteSuccess<PropertyBaseRootDataModel<SimpleMarykModel>>>(this)
        }

        val getResponse = dataStore.execute(
            addRequest.dataModel.get(keys[1])
        )
        assertTrue { getResponse.values.isEmpty() }
    }
}
