package maryk.datastore.memory

import kotlinx.coroutines.test.runTest
import maryk.core.models.RootDataModel
import maryk.core.models.graph
import maryk.core.properties.definitions.string
import maryk.core.query.changes.Change
import maryk.core.query.changes.ObjectCreate
import maryk.core.query.changes.change
import maryk.core.query.pairs.with
import maryk.core.query.requests.add
import maryk.core.query.requests.change
import maryk.core.query.requests.getChanges
import maryk.core.query.responses.statuses.AddSuccess
import maryk.core.query.responses.statuses.ChangeSuccess
import kotlin.test.Test
import kotlin.test.assertEquals

private object SingleHistoryModel : RootDataModel<SingleHistoryModel>() {
    val stable by string(1u)
    val changing by string(2u)
}

class SingleRecordHistoryTest {
    @Test
    fun retainedDeltaLimitKeepsCreationNormalizationProjectionAndVersionBounds() = runTest {
        val store = InMemoryDataStore.open(
            dataModelsById = mapOf(1u to SingleHistoryModel), keepAllVersions = true,
        )
        try {
            val created = store.execute(SingleHistoryModel.add(SingleHistoryModel.create {
                stable with "unchanged"
                changing with "first"
            })).statuses.single() as AddSuccess<SingleHistoryModel>
            val second = store.execute(SingleHistoryModel.change(created.key.change(
                Change(SingleHistoryModel.changing.ref() with "second"),
            ))).statuses.single() as ChangeSuccess<SingleHistoryModel>
            val third = store.execute(SingleHistoryModel.change(created.key.change(
                Change(SingleHistoryModel.changing.ref() with "third"),
            ))).statuses.single() as ChangeSuccess<SingleHistoryModel>

            val limited = store.execute(SingleHistoryModel.getChanges(created.key, maxVersions = 1u))
                .changes.single().changes
            assertEquals(listOf(created.version, third.version), limited.map { it.version })
            assertEquals(listOf(SingleHistoryModel.stable.ref()), limited.first().changes.filterIsInstance<Change>()
                .flatMap { it.referenceValuePairs }.map { it.reference })
            assertEquals("third", limited.last().changes.filterIsInstance<Change>()
                .single().referenceValuePairs.single().value)

            val selected = store.execute(SingleHistoryModel.getChanges(created.key, maxVersions = 1u,
                select = SingleHistoryModel.graph { listOf(changing) })).changes.single().changes
            assertEquals(listOf(ObjectCreate), selected.first().changes)
            assertEquals(listOf(created.version, third.version), selected.map { it.version })

            val bounded = store.execute(SingleHistoryModel.getChanges(created.key, maxVersions = 1u,
                toVersion = second.version)).changes.single().changes
            assertEquals(listOf(created.version, second.version), bounded.map { it.version })
            assertEquals("second", bounded.last().changes.filterIsInstance<Change>()
                .single().referenceValuePairs.single().value)

            val withoutCreation = store.execute(SingleHistoryModel.getChanges(created.key, maxVersions = 1u,
                fromVersion = second.version)).changes.single().changes
            assertEquals(listOf(third.version), withoutCreation.map { it.version })
        } finally {
            store.close()
        }
    }
}
