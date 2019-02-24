package maryk.datastore.memory

import maryk.core.properties.types.Key
import maryk.core.query.changes.ObjectSoftDeleteChange
import maryk.core.query.requests.delete
import maryk.core.query.requests.get
import maryk.core.query.requests.getChanges
import maryk.core.query.responses.statuses.AddSuccess
import maryk.core.query.responses.statuses.StatusType.SUCCESS
import maryk.core.query.responses.statuses.Success
import maryk.test.models.SimpleMarykModel
import maryk.test.requests.addRequest
import maryk.test.runSuspendingTest
import maryk.test.shouldBe
import maryk.test.shouldBeOfType
import kotlin.test.Test

class InMemoryDataStoreDeleteTest {
    private val dataStore = InMemoryDataStore()
    private val keys = mutableListOf<Key<SimpleMarykModel>>()

    init {
        runSuspendingTest {
            val addResponse = dataStore.execute(
                addRequest
            )
            addResponse.statuses.forEach { status ->
                val response = shouldBeOfType<AddSuccess<SimpleMarykModel>>(status)
                keys.add(response.key)
            }
        }
    }

    @Test
    fun executeDeleteRequest() = runSuspendingTest {
        val deleteResponse = dataStore.execute(
            SimpleMarykModel.delete(
                keys[0]
            )
        )

        deleteResponse.statuses.size shouldBe 1
        deleteResponse.statuses[0].statusType shouldBe SUCCESS
        with(deleteResponse.statuses[0]) {
            statusType shouldBe SUCCESS
            shouldBeOfType<Success<SimpleMarykModel>>(this)
        }

        val getResponse = dataStore.execute(
            addRequest.dataModel.get(keys[0])
        )
        getResponse.values.isEmpty() shouldBe true

        val getResponseWithDeleted = dataStore.execute(
            addRequest.dataModel.get(keys[0], filterSoftDeleted = false)
        )
        getResponseWithDeleted.values.isEmpty() shouldBe false
        getResponseWithDeleted.values[0].isDeleted shouldBe true

        val getChangesResponse = dataStore.execute(
            addRequest.dataModel.getChanges(keys[0])
        )

        getChangesResponse.changes.isEmpty() shouldBe true

        val getChangesWithDeletedResponse = dataStore.execute(
            addRequest.dataModel.getChanges(keys[0], filterSoftDeleted = false)
        )

        getChangesWithDeletedResponse.changes.size shouldBe 1
        getChangesWithDeletedResponse.changes[0].changes.size shouldBe 2
        getChangesWithDeletedResponse.changes[0].changes.last().let {
            it.changes.size shouldBe 1
            it.changes.first() shouldBe ObjectSoftDeleteChange(true)
        }
    }

    @Test
    fun processHardDeleteRequest() = runSuspendingTest {
        val deleteResponse = dataStore.execute(
            SimpleMarykModel.delete(
                keys[1],
                hardDelete = true
            )
        )

        deleteResponse.statuses.size shouldBe 1
        deleteResponse.statuses[0].statusType shouldBe SUCCESS
        with(deleteResponse.statuses[0]) {
            shouldBeOfType<Success<SimpleMarykModel>>(this)
        }

        val getResponse = dataStore.execute(
            addRequest.dataModel.get(keys[1])
        )
        getResponse.values.isEmpty() shouldBe true
    }
}
