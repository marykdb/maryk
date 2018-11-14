@file:Suppress("EXPERIMENTAL_API_USAGE")

package maryk.core.processors.datastore.memory

import maryk.core.properties.exceptions.InvalidValueException
import maryk.core.properties.types.Key
import maryk.core.query.changes.Check
import maryk.core.query.changes.change
import maryk.core.query.pairs.with
import maryk.core.query.requests.change
import maryk.core.query.responses.statuses.AddSuccess
import maryk.core.query.responses.statuses.Success
import maryk.core.query.responses.statuses.ValidationFail
import maryk.test.models.SimpleMarykModel
import maryk.test.requests.addRequest
import maryk.test.runSuspendingTest
import maryk.test.shouldBe
import maryk.test.shouldBeOfType
import kotlin.test.Test

@Suppress("EXPERIMENTAL_UNSIGNED_LITERALS")
class InMemoryDataStoreChangeTest {
    private val dataStore = InMemoryDataStore()
    private val keys = mutableListOf<Key<SimpleMarykModel>>()
    private val lastVersions = mutableListOf<ULong>()

    init {
        runSuspendingTest {
            val addResponse = dataStore.execute(
                addRequest
            )

            addResponse.statuses.forEach { status ->
                val response = shouldBeOfType<AddSuccess<SimpleMarykModel>>(status)
                keys.add(response.key)
                lastVersions.add(response.version)
            }
        }
    }

    @Test
    fun executeChangeCheckRequest() = runSuspendingTest {
        val changeResponse = dataStore.execute(
            SimpleMarykModel.change(
                keys[0].change(
                    Check(
                        SimpleMarykModel.ref { value } with "haha1"
                    ),
                    lastVersion = lastVersions[0]
                ),
                keys[0].change(
                    Check(
                        SimpleMarykModel.ref { value } with "wrong"
                    ),
                    lastVersion = lastVersions[0]
                ),
                keys[0].change(
                    Check(
                        SimpleMarykModel.ref { value } with "haha1"
                    ),
                    lastVersion = 123uL
                )
            )
        )

        changeResponse.statuses.size shouldBe 3
        changeResponse.statuses[0].let { status ->
            val success = shouldBeOfType<Success<*>>(status)
            shouldBeRecent(success.version, 1000uL)
        }

        changeResponse.statuses[1].let { status ->
            val validationFail = shouldBeOfType<ValidationFail<*>>(status)
            validationFail.exceptions.size shouldBe 1
            shouldBeOfType<InvalidValueException>(validationFail.exceptions[0])
        }

        changeResponse.statuses[2].let { status ->
            val validationFail = shouldBeOfType<ValidationFail<*>>(status)
            validationFail.exceptions.size shouldBe 1
            shouldBeOfType<InvalidValueException>(validationFail.exceptions[0])
        }
    }
}
