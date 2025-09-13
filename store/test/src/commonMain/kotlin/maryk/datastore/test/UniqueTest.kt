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
import maryk.core.query.requests.get
import maryk.core.query.responses.statuses.AddSuccess
import maryk.core.query.responses.statuses.ChangeSuccess
import maryk.core.query.responses.statuses.DeleteSuccess
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
        "checkUniqueChange" to ::checkUniqueChange,
        "checkUniqueChangeRelease" to ::checkUniqueChangeRelease,
        "checkUniqueChangeFailDoesNotChange" to ::checkUniqueChangeFailDoesNotChange,
        "checkUniqueAddDuplicate" to ::checkUniqueAddDuplicate,
    )

    override suspend fun initData() {
        val addItems = UniqueModel.add(
            UniqueModel.create { email with "test@test.com" },
            UniqueModel.create { email with "bla@bla.com" },
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
        ).statuses.forEach {
            assertStatusIs<DeleteSuccess<*>>(it)
        }
        this.keys.clear()
    }

    private val addUniqueItem = UniqueModel.add(
        UniqueModel.create { email with "test@test.com" }
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

    suspend fun checkUniqueChangeRelease() {
        val changeResponse = dataStore.execute(
            UniqueModel.change(
                keys[0].change(
                    Change(
                        UniqueModel { email::ref } with "new@test.com"
                    )
                )
            )
        )

        changeResponse.statuses.forEach { status ->
            assertStatusIs<ChangeSuccess<UniqueModel>>(status)
        }

        dataStore.execute(addUniqueItem).statuses.forEach { status ->
            assertStatusIs<AddSuccess<UniqueModel>>(status).also {
                keys.add(it.key)
            }
        }
    }

    suspend fun checkUniqueAddDuplicate() {
        val addResponse = dataStore.execute(
            UniqueModel.add(
                UniqueModel.create { email with "dup@test.com" },
                UniqueModel.create { email with "dup@test.com" },
            )
        )

        val firstStatus = assertStatusIs<AddSuccess<UniqueModel>>(addResponse.statuses[0])
        keys.add(firstStatus.key)

        val fail = assertStatusIs<ValidationFail<UniqueModel>>(addResponse.statuses[1])
        val alreadyExists = assertIs<AlreadyExistsException>(fail.exceptions.first())
        expect(UniqueModel { email::ref }) { alreadyExists.reference }
        expect(firstStatus.key) { alreadyExists.key }
    }

    suspend fun checkUniqueChangeFailDoesNotChange() {
        val changeResponse = dataStore.execute(
            UniqueModel.change(
                keys[1].change(
                    Change(
                        UniqueModel { email::ref } with "test@test.com"
                    )
                )
            )
        )

        val fail = assertStatusIs<ValidationFail<UniqueModel>>(changeResponse.statuses.first())
        assertIs<AlreadyExistsException>(fail.exceptions.first())

        val getResponse = dataStore.execute(
            UniqueModel.get(keys[1])
        )
        expect("bla@bla.com") { getResponse.values.first().values { email } }
    }
}
