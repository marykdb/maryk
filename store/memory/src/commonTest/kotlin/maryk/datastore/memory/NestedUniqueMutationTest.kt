package maryk.datastore.memory

import kotlinx.coroutines.test.runTest
import maryk.core.models.DataModel
import maryk.core.models.RootDataModel
import maryk.core.properties.definitions.embed
import maryk.core.properties.definitions.string
import maryk.core.query.changes.Change
import maryk.core.query.changes.ObjectSoftDeleteChange
import maryk.core.query.changes.change
import maryk.core.query.filters.Equals
import maryk.core.query.pairs.with
import maryk.core.query.requests.add
import maryk.core.query.requests.change
import maryk.core.query.requests.delete
import maryk.core.query.requests.get
import maryk.core.query.requests.scan
import maryk.core.query.responses.statuses.AddSuccess
import maryk.core.query.responses.statuses.ChangeSuccess
import maryk.core.query.responses.statuses.ValidationFail
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

private object UniqueDetails : DataModel<UniqueDetails>() {
    val email by string(1u, unique = true)
}

private object NestedUniqueRecord : RootDataModel<NestedUniqueRecord>() {
    val details by embed(1u, required = false, dataModel = { UniqueDetails })
}

class NestedUniqueMutationTest {
    @Test
    fun replacingDeletedEmbeddedValuesLeavesClaimsWithActiveOwner() = runTest {
        for (keepHistory in listOf(false, true)) {
            val store = InMemoryDataStore.open(keepAllVersions = keepHistory, dataModelsById = mapOf(1u to NestedUniqueRecord))
            fun values(email: String) = NestedUniqueRecord.create {
                details with UniqueDetails.create { this.email with email }
            }
            try {
                val deleted = assertIs<AddSuccess<NestedUniqueRecord>>(store.execute(NestedUniqueRecord.add(values("old@example.com"))).statuses.single()).key
                val active = assertIs<AddSuccess<NestedUniqueRecord>>(store.execute(NestedUniqueRecord.add(values("owned@example.com"))).statuses.single()).key
                assertIs<ChangeSuccess<NestedUniqueRecord>>(store.execute(NestedUniqueRecord.change(deleted.change(ObjectSoftDeleteChange(true)))).statuses.single())
                assertIs<ChangeSuccess<NestedUniqueRecord>>(store.execute(NestedUniqueRecord.change(
                    deleted.change(Change(NestedUniqueRecord.ref { details } with UniqueDetails.create { email with "owned@example.com" }))
                )).statuses.single())
                val filter = Equals(NestedUniqueRecord.ref { details { email } } with "owned@example.com")
                assertEquals(listOf(active), store.execute(NestedUniqueRecord.scan(where = filter)).values.map { it.key })
                assertIs<ValidationFail<NestedUniqueRecord>>(store.execute(NestedUniqueRecord.change(deleted.change(ObjectSoftDeleteChange(false)))).statuses.single())
                assertTrue(store.execute(NestedUniqueRecord.get(deleted, filterSoftDeleted = false)).values.single().isDeleted)
                store.execute(NestedUniqueRecord.delete(active, hardDelete = true))
                assertIs<ChangeSuccess<NestedUniqueRecord>>(store.execute(NestedUniqueRecord.change(deleted.change(ObjectSoftDeleteChange(false)))).statuses.single())
                assertEquals(listOf(deleted), store.execute(NestedUniqueRecord.scan(where = filter)).values.map { it.key })
            } finally { store.close() }
        }
    }

    @Test
    fun replacingAndDeletingEmbeddedValuesReconcilesUniqueDescendants() = runTest {
        for (keepHistory in listOf(false, true)) {
            val store = InMemoryDataStore.open(keepAllVersions = keepHistory, dataModelsById = mapOf(1u to NestedUniqueRecord))
            fun values(email: String) = NestedUniqueRecord.create {
                details with UniqueDetails.create { this.email with email }
            }
            try {
                val added = assertIs<AddSuccess<NestedUniqueRecord>>(store.execute(NestedUniqueRecord.add(values("old@example.com"))).statuses.single())
                val changed = assertIs<ChangeSuccess<NestedUniqueRecord>>(store.execute(NestedUniqueRecord.change(
                    added.key.change(Change(NestedUniqueRecord.ref { details } with UniqueDetails.create { email with "new@example.com" }))
                )).statuses.single())
                val second = assertIs<AddSuccess<NestedUniqueRecord>>(store.execute(NestedUniqueRecord.add(values("old@example.com"))).statuses.single())
                assertIs<ValidationFail<NestedUniqueRecord>>(store.execute(NestedUniqueRecord.add(values("new@example.com"))).statuses.single())
                assertIs<ValidationFail<NestedUniqueRecord>>(store.execute(NestedUniqueRecord.change(
                    added.key.change(Change(NestedUniqueRecord.ref { details } with UniqueDetails.create { email with "old@example.com" }))
                )).statuses.single())
                assertEquals("new@example.com", store.execute(NestedUniqueRecord.get(added.key)).values.single().values { details }?.get(UniqueDetails.ref { email }))
                val oldFilter = Equals(NestedUniqueRecord.ref { details { email } } with "old@example.com")
                assertEquals(listOf(second.key), store.execute(NestedUniqueRecord.scan(where = oldFilter)).values.map { it.key })
                if (keepHistory) {
                    assertEquals(listOf(added.key), store.execute(NestedUniqueRecord.scan(where = oldFilter, toVersion = added.version)).values.map { it.key })
                    assertEquals(emptyList(), store.execute(NestedUniqueRecord.scan(where = oldFilter, toVersion = changed.version)).values)
                }
                assertIs<ChangeSuccess<NestedUniqueRecord>>(store.execute(NestedUniqueRecord.change(
                    added.key.change(Change(NestedUniqueRecord.ref { details } with null))
                )).statuses.single())
                assertIs<AddSuccess<NestedUniqueRecord>>(store.execute(NestedUniqueRecord.add(values("new@example.com"))).statuses.single())
            } finally { store.close() }
        }
    }
}
