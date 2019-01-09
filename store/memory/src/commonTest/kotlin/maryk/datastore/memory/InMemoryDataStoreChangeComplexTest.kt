@file:Suppress("EXPERIMENTAL_API_USAGE")

package maryk.datastore.memory

import maryk.core.properties.types.Key
import maryk.core.properties.types.TypedValue
import maryk.core.query.changes.Delete
import maryk.core.query.changes.change
import maryk.core.query.requests.add
import maryk.core.query.requests.change
import maryk.core.query.requests.get
import maryk.core.query.responses.statuses.AddSuccess
import maryk.core.query.responses.statuses.Success
import maryk.test.models.ComplexModel
import maryk.test.models.EmbeddedMarykModel
import maryk.test.models.Option.V3
import maryk.test.runSuspendingTest
import maryk.test.shouldBe
import maryk.test.shouldBeOfType
import kotlin.test.Test

@Suppress("EXPERIMENTAL_UNSIGNED_LITERALS")
class InMemoryDataStoreChangeComplexTest {
    private val dataStore = InMemoryDataStore()
    private val keys = mutableListOf<Key<ComplexModel>>()
    private val lastVersions = mutableListOf<ULong>()

    init {
        runSuspendingTest {
            val addResponse = dataStore.execute(
                ComplexModel.add(
                    ComplexModel(
                        multi = TypedValue(V3, EmbeddedMarykModel("u3", EmbeddedMarykModel("ue3")))
                    ),
                    ComplexModel(
                        mapStringString = mapOf("a" to "b", "c" to "d")
                    )
                )
            )

            addResponse.statuses.forEach { status ->
                val response = shouldBeOfType<AddSuccess<ComplexModel>>(status)
                keys.add(response.key)
                lastVersions.add(response.version)
            }
        }
    }

    @Test
    fun executeChangeDeleteMultiRequest() = runSuspendingTest {
        val changeResponse = dataStore.execute(
            ComplexModel.change(
                keys[0].change(
                    Delete(ComplexModel.ref { multi })
                )
            )
        )

        changeResponse.statuses.size shouldBe 1
        changeResponse.statuses[0].let { status ->
            val success = shouldBeOfType<Success<*>>(status)
            shouldBeRecent(success.version, 1000uL)
        }

        val getResponse = dataStore.execute(
            ComplexModel.get(keys[0])
        )

        getResponse.values.size shouldBe 0
    }

    @Test
    fun executeChangeDeleteCompleteMapRequest() = runSuspendingTest {
        val changeResponse = dataStore.execute(
            ComplexModel.change(
                keys[1].change(
                    Delete(ComplexModel.ref { mapStringString })
                )
            )
        )

        changeResponse.statuses.size shouldBe 1
        changeResponse.statuses[0].let { status ->
            val success = shouldBeOfType<Success<*>>(status)
            shouldBeRecent(success.version, 1000uL)
        }

        val getResponse = dataStore.execute(
            ComplexModel.get(keys[1])
        )

        getResponse.values.size shouldBe 0
    }
}
