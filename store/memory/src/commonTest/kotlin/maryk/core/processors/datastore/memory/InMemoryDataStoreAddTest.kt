@file:Suppress("EXPERIMENTAL_API_USAGE")

package maryk.core.processors.datastore.memory

import maryk.core.objects.Values
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
class InMemoryDataStoreAddTest {
    private val dataStore = InMemoryDataStore()

    @Test
    fun executeAddAndSimpleGetRequest() = runSuspendingTest {
        val addResponse = dataStore.execute(
            addRequest
        )

        addResponse.dataModel shouldBe addRequest.dataModel
        addResponse.statuses.count() shouldBe 2

        val keysToOriginal = mutableMapOf<Key<*>, Values<SimpleMarykModel, *>>()
        val keys = mutableListOf<Key<SimpleMarykModel>>()
        addResponse.statuses.forEachIndexed { index, it ->
            val response = shouldBeOfType<AddSuccess<SimpleMarykModel>>(it)
            shouldBeRecent(response.version, 1000uL)
            response.changes.isEmpty() shouldBe true
            shouldBeOfType<Key<SimpleMarykModel>>(response.key).size shouldBe 16
            keys.add(response.key)
            keysToOriginal[response.key] = addRequest.objectsToAdd[index]
        }

        val getResponse = dataStore.execute(
            addRequest.dataModel.get(*keys.toTypedArray())
        )

        getResponse.values.size shouldBe 2

        getResponse.values.forEachIndexed { index, value ->
            value.values shouldBe addRequest.objectsToAdd[index]
        }
    }
}
