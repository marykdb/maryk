@file:Suppress("EXPERIMENTAL_API_USAGE")

package maryk.datastore.memory

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
    private var lowestVersion = ULong.MAX_VALUE

    init {
        runSuspendingTest {
            val addResponse = dataStore.execute(
                addRequest
            )
            addResponse.statuses.forEach { status ->
                val response = shouldBeOfType<AddSuccess<SimpleMarykModel>>(status)
                keys.add(response.key)
                if (response.version < lowestVersion) {
                    // Add lowest version for scan test
                    lowestVersion = response.version
                }
            }
        }
    }

    @Test
    fun executeSimpleGetRequest() = runSuspendingTest {
        val getResponse = dataStore.execute(
            SimpleMarykModel.get(*keys.toTypedArray())
        )

        getResponse.values.size shouldBe 2

        getResponse.values.forEachIndexed { index, value ->
            value.values shouldBe addRequest.objectsToAdd[index]
        }
    }

    @Test
    fun executeToVersionGetRequest() = runSuspendingTest {
        val getResponse = dataStore.execute(
            SimpleMarykModel.get(*keys.toTypedArray(), toVersion = lowestVersion - 1uL)
        )

        getResponse.values.size shouldBe 0
    }

    @Test
    fun executeGetRequestWithSelect() = runSuspendingTest {
        val scanResponse = dataStore.execute(
            SimpleMarykModel.get(
                *keys.toTypedArray(),
                select = SimpleMarykModel.graph {
                    listOf(value)
                }
            )
        )

        scanResponse.values.size shouldBe 2

        scanResponse.values[0].let {
            it.values shouldBe SimpleMarykModel(value = "haha1")
            it.key shouldBe keys[0]
        }
    }
}
