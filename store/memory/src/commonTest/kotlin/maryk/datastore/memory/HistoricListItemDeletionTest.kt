package maryk.datastore.memory

import kotlinx.coroutines.test.runTest
import maryk.core.models.RootDataModel
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.definitions.list
import maryk.core.query.changes.Change
import maryk.core.query.changes.ListChange
import maryk.core.query.changes.change
import maryk.core.query.pairs.with
import maryk.core.query.requests.add
import maryk.core.query.requests.change
import maryk.core.query.requests.get
import maryk.core.query.responses.statuses.AddSuccess
import maryk.core.query.responses.statuses.ChangeSuccess
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

private object HistoricListRecord : RootDataModel<HistoricListRecord>() {
    val items by list(1u, valueDefinition = StringDefinition())
}

class HistoricListItemDeletionTest {
    @Test
    fun deletionShiftsCurrentValuesAfterEditsAndRetainedTailTombstones() = runTest {
        for (keepHistory in listOf(false, true)) {
            val store = InMemoryDataStore.open(
                keepAllVersions = keepHistory,
                dataModelsById = mapOf(1u to HistoricListRecord),
            )
            try {
                val initial = listOf("a", "b", "c", "d")
                val added = assertIs<AddSuccess<HistoricListRecord>>(store.execute(HistoricListRecord.add(
                    HistoricListRecord.create { items with initial },
                )).statuses.single())
                val edited = assertIs<ChangeSuccess<HistoricListRecord>>(store.execute(HistoricListRecord.change(
                    added.key.change(Change(
                        HistoricListRecord.ref { items at 1u } with "B",
                        HistoricListRecord.ref { items at 2u } with "C",
                    )),
                )).statuses.single())
                val deleted = assertIs<ChangeSuccess<HistoricListRecord>>(store.execute(HistoricListRecord.change(
                    added.key.change(Change(HistoricListRecord.ref { items at 0u } with null)),
                )).statuses.single())
                assertEquals(listOf("B", "C", "d"), store.execute(HistoricListRecord.get(added.key)).values.single().values { items })

                val shortened = assertIs<ChangeSuccess<HistoricListRecord>>(store.execute(HistoricListRecord.change(
                    added.key.change(Change(HistoricListRecord.ref { items } with listOf("x", "y"))),
                )).statuses.single())
                assertIs<ChangeSuccess<HistoricListRecord>>(store.execute(HistoricListRecord.change(
                    added.key.change(
                        Change(HistoricListRecord.ref { items at 1u } with "Y"),
                        Change(HistoricListRecord.ref { items at 0u } with null),
                        ListChange(HistoricListRecord.ref { items }.change(addValuesToEnd = listOf("z", "w"))),
                        Change(HistoricListRecord.ref { items at 1u } with null),
                    ),
                )).statuses.single())
                assertEquals(listOf("Y", "w"), store.execute(HistoricListRecord.get(added.key)).values.single().values { items })
                if (keepHistory) {
                    assertEquals(initial, store.execute(HistoricListRecord.get(added.key, toVersion = added.version)).values.single().values { items })
                    assertEquals(listOf("a", "B", "C", "d"), store.execute(HistoricListRecord.get(added.key, toVersion = edited.version)).values.single().values { items })
                    assertEquals(listOf("B", "C", "d"), store.execute(HistoricListRecord.get(added.key, toVersion = deleted.version)).values.single().values { items })
                    assertEquals(listOf("x", "y"), store.execute(HistoricListRecord.get(added.key, toVersion = shortened.version)).values.single().values { items })
                }
            } finally {
                store.close()
            }
        }
    }
}
