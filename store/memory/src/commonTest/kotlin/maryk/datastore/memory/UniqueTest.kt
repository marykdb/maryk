package maryk.datastore.memory

import maryk.core.models.RootDataModel
import maryk.core.properties.PropertyDefinitions
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.exceptions.AlreadySetException
import maryk.core.properties.types.Key
import maryk.core.query.requests.add
import maryk.core.query.responses.statuses.AddSuccess
import maryk.core.query.responses.statuses.ValidationFail
import maryk.datastore.memory.UniqueModel.Properties
import maryk.test.runSuspendingTest
import maryk.test.shouldBe
import maryk.test.shouldBeOfType
import kotlin.test.Test

object UniqueModel: RootDataModel<UniqueModel, Properties>(
    name = "SimpleMarykModel",
    properties = Properties
) {
    object Properties : PropertyDefinitions() {
        val email = add(
            index = 1, name = "email",
            definition = StringDefinition(
                unique = true
            )
        )
    }

    operator fun invoke(email: String) = this.values {
        mapNonNulls(this.email with email)
    }
}

class UniqueTest {
    private val dataStore = InMemoryDataStore()
    private val keys = mutableListOf<Key<UniqueModel>>()

    init {
        runSuspendingTest {
            val addResponse = dataStore.execute(
                UniqueModel.add(
                    UniqueModel(email = "test@test.com"),
                    UniqueModel(email = "bla@bla.com")
                )
            )
            addResponse.statuses.forEach { status ->
                val response = shouldBeOfType<AddSuccess<UniqueModel>>(status)
                keys.add(response.key)
            }
        }
    }

    @Test
    fun testUniqueCheck() = runSuspendingTest {
        val addResponse = dataStore.execute(
            UniqueModel.add(
                UniqueModel(email = "test@test.com")
            )
        )
        addResponse.statuses.forEach { status ->
            val fail = shouldBeOfType<ValidationFail<UniqueModel>>(status)
            val alreadySet = shouldBeOfType<AlreadySetException>(fail.exceptions.first())
            alreadySet.reference shouldBe UniqueModel.ref { email }
        }
    }
}
