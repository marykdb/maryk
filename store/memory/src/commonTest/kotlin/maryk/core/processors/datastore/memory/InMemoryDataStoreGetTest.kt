@file:Suppress("EXPERIMENTAL_API_USAGE")

package maryk.core.processors.datastore.memory

import maryk.core.properties.types.Key
import maryk.core.query.requests.get
import maryk.core.query.responses.statuses.AddSuccess
import maryk.test.models.SimpleMarykModel
import maryk.test.requests.addRequest
import maryk.test.runSuspendingTest
import maryk.test.shouldBe
import maryk.test.shouldBeOfType
import kotlin.test.Test

@Suppress("EXPERIMENTAL_UNSIGNED_LITERALS")
class InMemoryDataStoreGetTest {
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
    fun executeAddAndSimpleGetRequest() = runSuspendingTest {
        val getResponse = dataStore.execute(
            SimpleMarykModel.get(*keys.toTypedArray())
        )

        getResponse.values.size shouldBe 2

        getResponse.values.forEachIndexed { index, value ->
            value.values shouldBe addRequest.objectsToAdd[index]
        }
    }
}
