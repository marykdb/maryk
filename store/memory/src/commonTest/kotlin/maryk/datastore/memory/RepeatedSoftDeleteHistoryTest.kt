package maryk.datastore.memory

import kotlinx.coroutines.test.runTest
import maryk.core.models.RootDataModel
import maryk.core.properties.definitions.string
import maryk.core.query.changes.ObjectSoftDeleteChange
import maryk.core.query.changes.change
import maryk.core.query.pairs.with
import maryk.core.query.requests.add
import maryk.core.query.requests.change
import maryk.core.query.requests.delete
import maryk.core.query.requests.get
import maryk.core.query.requests.getChanges
import maryk.core.query.responses.statuses.AddSuccess
import maryk.core.query.responses.statuses.ChangeSuccess
import maryk.core.query.responses.statuses.DeleteSuccess
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

private object RepeatedDeleteRecord : RootDataModel<RepeatedDeleteRecord>() {
    val value by string(1u)
}

class RepeatedSoftDeleteHistoryTest {
    @Test
    fun laterSoftDeletesAreCommittedBeforeHistoricalReadsAndUndelete() = runTest {
        val store = InMemoryDataStore.open(keepAllVersions = true, dataModelsById = mapOf(1u to RepeatedDeleteRecord))
        try {
            val added = assertIs<AddSuccess<RepeatedDeleteRecord>>(store.execute(RepeatedDeleteRecord.add(
                RepeatedDeleteRecord.create { value with "retained" },
            )).statuses.single())
            assertIs<DeleteSuccess<RepeatedDeleteRecord>>(store.execute(RepeatedDeleteRecord.delete(added.key)).statuses.single())
            assertIs<ChangeSuccess<RepeatedDeleteRecord>>(store.execute(RepeatedDeleteRecord.change(
                added.key.change(ObjectSoftDeleteChange(false)),
            )).statuses.single())
            val secondDelete = assertIs<DeleteSuccess<RepeatedDeleteRecord>>(store.execute(RepeatedDeleteRecord.delete(added.key)).statuses.single())
            assertTrue(store.execute(RepeatedDeleteRecord.get(added.key, toVersion = secondDelete.version, filterSoftDeleted = false)).values.single().isDeleted)
            assertEquals(emptyList(), store.execute(RepeatedDeleteRecord.get(added.key, toVersion = secondDelete.version)).values)
            assertEquals(listOf(ObjectSoftDeleteChange(true)), store.execute(RepeatedDeleteRecord.getChanges(
                added.key, fromVersion = secondDelete.version, toVersion = secondDelete.version, filterSoftDeleted = false,
            )).changes.single().changes.single().changes)

            assertIs<ChangeSuccess<RepeatedDeleteRecord>>(store.execute(RepeatedDeleteRecord.change(
                added.key.change(ObjectSoftDeleteChange(false)),
            )).statuses.single())
            assertTrue(store.execute(RepeatedDeleteRecord.get(added.key, toVersion = secondDelete.version, filterSoftDeleted = false)).values.single().isDeleted)
            assertEquals(listOf(ObjectSoftDeleteChange(true)), store.execute(RepeatedDeleteRecord.getChanges(
                added.key, fromVersion = secondDelete.version, toVersion = secondDelete.version, filterSoftDeleted = false,
            )).changes.single().changes.single().changes)
        } finally {
            store.close()
        }
    }
}
