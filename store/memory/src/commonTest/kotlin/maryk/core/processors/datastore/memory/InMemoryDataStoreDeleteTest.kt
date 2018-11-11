@file:Suppress("EXPERIMENTAL_API_USAGE")

package maryk.core.processors.datastore.memory

import maryk.core.properties.types.Key
import maryk.core.query.requests.delete
import maryk.core.query.requests.get
import maryk.core.query.responses.statuses.AddSuccess
import maryk.core.query.responses.statuses.StatusType.SUCCESS
import maryk.core.query.responses.statuses.Success
import maryk.test.models.SimpleMarykModel
import maryk.test.requests.addRequest
import maryk.test.runSuspendingTest
import maryk.test.shouldBe
import maryk.test.shouldBeOfType
import kotlin.test.Test

@Suppress("EXPERIMENTAL_UNSIGNED_LITERALS")
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
        with (deleteResponse.statuses[0]) {
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
        with (deleteResponse.statuses[0]) {
            shouldBeOfType<Success<SimpleMarykModel>>(this)
        }

        val getResponse = dataStore.execute(
            addRequest.dataModel.get(keys[1])
        )
        getResponse.values.isEmpty() shouldBe true
    }
}
