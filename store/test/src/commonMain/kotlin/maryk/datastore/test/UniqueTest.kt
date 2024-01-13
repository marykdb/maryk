package maryk.datastore.test

import maryk.core.models.RootDataModel
import maryk.core.properties.definitions.string
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
import kotlin.test.assertIs
import kotlin.test.expect

object UniqueModel : RootDataModel<UniqueModel>() {
    val email by string(1u, unique = true)
}

class UniqueTest(
    val dataStore: IsDataStore
) : IsDataStoreTest {
    private val keys = mutableListOf<Key<UniqueModel>>()

    override val allTests = mapOf(
        "checkUnique" to ::checkUnique,
        "checkUniqueChange" to ::checkUniqueChange
    )

    override suspend fun initData() {
        val addItems = UniqueModel.add(
            UniqueModel.run { create(email with "test@test.com") },
            UniqueModel.run { create(email with "bla@bla.com") },
        )

        dataStore.execute(addItems).also {
            it.statuses.forEach { status ->
                val response = assertStatusIs<AddSuccess<UniqueModel>>(status)
                keys.add(response.key)
            }
        }
    }

    override suspend fun resetData() {
        dataStore.execute(
            UniqueModel.delete(*keys.toTypedArray(), hardDelete = true)
        )
        this.keys.clear()
    }

    private val addUniqueItem = UniqueModel.add(
        UniqueModel.run { create(email with "test@test.com") }
    )

    suspend fun checkUnique() {
        val addResponse = dataStore.execute(addUniqueItem)
        addResponse.statuses.forEach { status ->
            val fail = assertStatusIs<ValidationFail<UniqueModel>>(status)
            val alreadyExists = assertIs<AlreadyExistsException>(fail.exceptions.first())
            expect(UniqueModel { email::ref }) { alreadyExists.reference }
            expect(keys[0]) { alreadyExists.key }
        }

        dataStore.execute(UniqueModel.delete(keys[0]))

        dataStore.execute(addUniqueItem).statuses.forEach { status ->
            assertStatusIs<AddSuccess<UniqueModel>>(status).also {
                keys.add(it.key)
            }
        }
    }

    suspend fun checkUniqueChange() {
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
            val fail = assertStatusIs<ValidationFail<UniqueModel>>(status)
            val alreadyExists = assertIs<AlreadyExistsException>(fail.exceptions.first())
            expect(UniqueModel { email::ref }) { alreadyExists.reference }
            expect(keys[0]) { alreadyExists.key }
        }

        dataStore.execute(UniqueModel.delete(keys[0]))

        dataStore.execute(addUniqueItem).statuses.forEach { status ->
            assertStatusIs<AddSuccess<UniqueModel>>(status).also {
                keys.add(it.key)
            }
        }
    }
}
