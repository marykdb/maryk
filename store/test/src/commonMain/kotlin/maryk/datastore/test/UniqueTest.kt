package maryk.datastore.test

import maryk.core.models.RootDataModel
import maryk.core.properties.PropertyDefinitions
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.exceptions.AlreadyExistsException
import maryk.core.properties.types.Key
import maryk.core.query.changes.Change
import maryk.core.query.changes.change
import maryk.core.query.pairs.with
import maryk.core.query.requests.add
import maryk.core.query.requests.change
import maryk.core.query.requests.delete
import maryk.core.query.responses.statuses.AddSuccess
import maryk.core.query.responses.statuses.ValidationFail
import maryk.datastore.shared.IsDataStore
import maryk.datastore.test.UniqueModel.Properties
import maryk.test.assertType
import maryk.test.runSuspendingTest
import kotlin.test.Test
import kotlin.test.expect

object UniqueModel : RootDataModel<UniqueModel, Properties>(
    properties = Properties
) {
    object Properties : PropertyDefinitions() {
        val email by wrap(1u){
            StringDefinition(
                unique = true
            )
        }
    }

    operator fun invoke(email: String) = this.values {
        mapNonNulls(this.email with email)
    }
}

class UniqueTest(
    val dataStore: IsDataStore
) : IsDataStoreTest {
    private val keys = mutableListOf<Key<UniqueModel>>()

    override val allTests = mapOf(
        "checkUnique" to ::checkUnique,
        "checkUniqueChange" to ::checkUniqueChange
    )

    override fun initData() {
        val addItems = UniqueModel.add(
            UniqueModel(email = "test@test.com"),
            UniqueModel(email = "bla@bla.com")
        )

        runSuspendingTest {
            dataStore.execute(addItems).also {
                it.statuses.forEach { status ->
                    val response = assertType<AddSuccess<UniqueModel>>(status)
                    keys.add(response.key)
                }
            }
        }
    }

    override fun resetData() {
        runSuspendingTest {
            dataStore.execute(
                UniqueModel.delete(*keys.toTypedArray(), hardDelete = true)
            )
        }
        this.keys.clear()
    }

    private val addUniqueItem = UniqueModel.add(
        UniqueModel(email = "test@test.com")
    )

    @Test
    fun checkUnique() = runSuspendingTest {
        val addResponse = dataStore.execute(addUniqueItem)
        addResponse.statuses.forEach { status ->
            val fail = assertType<ValidationFail<UniqueModel>>(status)
            val alreadyExists = assertType<AlreadyExistsException>(fail.exceptions.first())
            expect(UniqueModel { email::ref }) { alreadyExists.reference }
            expect(keys[0]) { alreadyExists.key }
        }

        dataStore.execute(UniqueModel.delete(keys[0]))

        dataStore.execute(addUniqueItem).statuses.forEach { status ->
            assertType<AddSuccess<UniqueModel>>(status).also {
                keys.add(it.key)
            }
        }
    }

    @Test
    fun checkUniqueChange() = runSuspendingTest {
        val changeResponse = dataStore.execute(
            UniqueModel.change(
                keys[1].change(
                    Change(
                        UniqueModel { email::ref } with "test@test.com"
                    )
                )
            )
        )
        changeResponse.statuses.forEach { status ->
            val fail = assertType<ValidationFail<UniqueModel>>(status)
            val alreadyExists = assertType<AlreadyExistsException>(fail.exceptions.first())
            expect(UniqueModel { email::ref }) { alreadyExists.reference }
            expect(keys[0]) { alreadyExists.key }
        }

        dataStore.execute(UniqueModel.delete(keys[0]))

        dataStore.execute(addUniqueItem).statuses.forEach { status ->
            assertType<AddSuccess<UniqueModel>>(status).also {
                keys.add(it.key)
            }
        }
    }
}
