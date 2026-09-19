package maryk.datastore.memory

import kotlinx.coroutines.test.runTest
import maryk.core.query.filters.Equals
import maryk.core.query.pairs.with
import maryk.core.query.requests.add
import maryk.core.query.requests.delete
import maryk.core.query.requests.get
import maryk.core.query.requests.scan
import maryk.core.query.responses.statuses.AddSuccess
import maryk.core.query.responses.statuses.DeleteSuccess
import maryk.core.query.responses.statuses.ValidationFail
import maryk.datastore.test.NullableUniqueModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class InMemoryUniqueOwnershipTest {
    @Test
    fun hardDeleteOfFormerOwnerKeepsReplacementUniqueLookup() = runTest {
        val dataStore = InMemoryDataStore.open(
            keepAllVersions = false,
            dataModelsById = mapOf(1u to NullableUniqueModel)
        )
        try {
            val values = NullableUniqueModel.create { email with "claimed@example.test" }
            val original = assertIs<AddSuccess<NullableUniqueModel>>(
                dataStore.execute(NullableUniqueModel.add(values)).statuses.single()
            )
            assertIs<DeleteSuccess<NullableUniqueModel>>(
                dataStore.execute(NullableUniqueModel.delete(original.key)).statuses.single()
            )
            val replacement = assertIs<AddSuccess<NullableUniqueModel>>(
                dataStore.execute(NullableUniqueModel.add(values)).statuses.single()
            )
            assertIs<DeleteSuccess<NullableUniqueModel>>(
                dataStore.execute(NullableUniqueModel.delete(original.key, hardDelete = true)).statuses.single()
            )

            assertEquals(replacement.key, dataStore.execute(NullableUniqueModel.get(replacement.key)).values.single().key)
            assertEquals(listOf(replacement.key), dataStore.execute(NullableUniqueModel.scan(
                where = Equals(NullableUniqueModel.email.ref() with "claimed@example.test")
            )).values.map { it.key })
            assertIs<ValidationFail<NullableUniqueModel>>(
                dataStore.execute(NullableUniqueModel.add(values)).statuses.single()
            )
        } finally {
            dataStore.close()
        }
    }
}
