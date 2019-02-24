package maryk.datastore.memory

import maryk.core.models.RootDataModel
import maryk.core.properties.PropertyDefinitions
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.exceptions.AlreadySetException
import maryk.core.properties.types.Key
import maryk.core.query.changes.Change
import maryk.core.query.changes.change
import maryk.core.query.pairs.with
import maryk.core.query.requests.add
import maryk.core.query.requests.change
import maryk.core.query.requests.delete
import maryk.core.query.responses.statuses.AddSuccess
import maryk.core.query.responses.statuses.ValidationFail
import maryk.datastore.memory.UniqueModel.Properties
import maryk.test.runSuspendingTest
import maryk.test.shouldBe
import maryk.test.shouldBeOfType
import kotlin.test.Test

object UniqueModel : RootDataModel<UniqueModel, Properties>(
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

    private val dataStoreWithHistory = InMemoryDataStore(true)
    private val keysForHistory = mutableListOf<Key<UniqueModel>>()

    init {
        val addItems = UniqueModel.add(
            UniqueModel(email = "test@test.com"),
            UniqueModel(email = "bla@bla.com")
        )

        runSuspendingTest {
            dataStore.execute(addItems).also {
                it.statuses.forEach { status ->
                    val response = shouldBeOfType<AddSuccess<UniqueModel>>(status)
                    keys.add(response.key)
                }
            }
            dataStoreWithHistory.execute(addItems).also {
                it.statuses.forEach { status ->
                    val response = shouldBeOfType<AddSuccess<UniqueModel>>(status)
                    keysForHistory.add(response.key)
                }
            }
        }
    }

    private val addUniqueItem = UniqueModel.add(
        UniqueModel(email = "test@test.com")
    )

    @Test
    fun checkUnique() = runSuspendingTest {
        val addResponse = dataStore.execute(addUniqueItem)
        addResponse.statuses.forEach { status ->
            val fail = shouldBeOfType<ValidationFail<UniqueModel>>(status)
            val alreadySet = shouldBeOfType<AlreadySetException>(fail.exceptions.first())
            alreadySet.reference shouldBe UniqueModel.ref { email }
        }

        dataStore.execute(UniqueModel.delete(keys[0]))

        dataStore.execute(addUniqueItem).statuses.forEach { status ->
            shouldBeOfType<AddSuccess<UniqueModel>>(status)
        }
    }

    @Test
    fun checkUniqueWithHistory() = runSuspendingTest {
        val addResponse = dataStoreWithHistory.execute(addUniqueItem)

        addResponse.statuses.forEach { status ->
            val fail = shouldBeOfType<ValidationFail<UniqueModel>>(status)
            val alreadySet = shouldBeOfType<AlreadySetException>(fail.exceptions.first())
            alreadySet.reference shouldBe UniqueModel.ref { email }
        }

        dataStoreWithHistory.execute(UniqueModel.delete(keysForHistory[0]))

        dataStoreWithHistory.execute(addUniqueItem).statuses.forEach { status ->
            shouldBeOfType<AddSuccess<UniqueModel>>(status)
        }
    }

    @Test
    fun checkUniqueChange() = runSuspendingTest {
        val changeResponse = dataStore.execute(
            UniqueModel.change(
                keys[1].change(
                    Change(
                        UniqueModel.ref { email } with "test@test.com"
                    )
                )
            )
        )
        changeResponse.statuses.forEach { status ->
            val fail = shouldBeOfType<ValidationFail<UniqueModel>>(status)
            val alreadySet = shouldBeOfType<AlreadySetException>(fail.exceptions.first())
            alreadySet.reference shouldBe UniqueModel.ref { email }
        }

        dataStore.execute(UniqueModel.delete(keys[0]))

        dataStore.execute(addUniqueItem).statuses.forEach { status ->
            shouldBeOfType<AddSuccess<UniqueModel>>(status)
        }
    }
}
