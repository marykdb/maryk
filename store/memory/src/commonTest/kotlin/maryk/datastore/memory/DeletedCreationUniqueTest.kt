package maryk.datastore.memory

import kotlinx.coroutines.test.runTest
import maryk.core.models.key
import maryk.core.query.changes.ObjectSoftDeleteChange
import maryk.core.query.changes.change
import maryk.core.query.filters.Equals
import maryk.core.query.pairs.with
import maryk.core.query.requests.add
import maryk.core.query.requests.change
import maryk.core.query.requests.scan
import maryk.core.query.responses.UpdateResponse
import maryk.core.query.responses.statuses.ChangeSuccess
import maryk.core.query.responses.statuses.ValidationFail
import maryk.core.query.responses.updates.AdditionUpdate
import maryk.datastore.test.UniqueModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class DeletedCreationUniqueTest {
    @Test
    fun undeleteClaimsUniqueValuesOfInitiallyDeletedCreation() = runTest {
        for (keepHistory in listOf(false, true)) {
            val store = InMemoryDataStore.open(keepAllVersions = keepHistory, dataModelsById = mapOf(1u to UniqueModel))
            try {
                val values = UniqueModel.create { email with "deleted-at-creation@example.com" }
                val key = UniqueModel.key(values)
                store.processUpdate(UpdateResponse(UniqueModel, AdditionUpdate(
                    key = key, version = 100uL, firstVersion = 100uL, insertionIndex = 0, isDeleted = true, values = values,
                )))
                val undeleted = assertIs<ChangeSuccess<UniqueModel>>(store.execute(
                    UniqueModel.change(key.change(ObjectSoftDeleteChange(false)))
                ).statuses.single())
                val filter = Equals(UniqueModel.ref { email } with "deleted-at-creation@example.com")
                assertEquals(listOf(key), store.execute(UniqueModel.scan(where = filter)).values.map { it.key })
                assertIs<ValidationFail<UniqueModel>>(store.execute(UniqueModel.add(values)).statuses.single())
                if (keepHistory) {
                    assertEquals(emptyList(), store.execute(UniqueModel.scan(where = filter, toVersion = 100uL)).values)
                    assertEquals(listOf(key), store.execute(UniqueModel.scan(where = filter, toVersion = undeleted.version)).values.map { it.key })
                }
            } finally { store.close() }
        }
    }
}
