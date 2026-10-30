package maryk.datastore.memory

import kotlinx.coroutines.test.runTest
import maryk.core.properties.types.MutableTypedValue
import maryk.core.query.changes.Change
import maryk.core.query.changes.change
import maryk.core.query.pairs.with
import maryk.core.query.requests.add
import maryk.core.query.requests.change
import maryk.core.query.requests.get
import maryk.core.query.requests.getChanges
import maryk.core.query.responses.statuses.AddSuccess
import maryk.core.query.responses.statuses.ChangeSuccess
import maryk.test.models.ComplexModel
import maryk.test.models.MarykTypeEnum.T1
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs

class TypedCellOwnershipTest {
    @Test
    fun addingMutableTypedMapItemsDoesNotRetainTheirWrappers() = runTest {
        for (history in listOf(false, true)) {
            val store = InMemoryDataStore.open(dataModelsById = mapOf(1u to ComplexModel), keepAllVersions = history)
            try {
                val supplied = MutableTypedValue(T1, "original")
                val original = ComplexModel.create(setDefaults = false) { mapIntMulti with mapOf(1u to supplied) }
                val added = assertIs<AddSuccess<ComplexModel>>(store.execute(ComplexModel.add(original)).statuses.single())
                supplied.value = "changed outside the store"

                val current = store.execute(ComplexModel.get(added.key)).values.single()
                val item = current.values { mapIntMulti }!!.getValue(1u)
                assertEquals("original", item.value)
                assertFalse(item is MutableTypedValue<*, *>)
                assertEquals(added.version, current.lastVersion)
                if (history) {
                    assertEquals("original", store.execute(ComplexModel.get(added.key, toVersion = added.version))
                        .values.single().values { mapIntMulti }!!.getValue(1u).value)
                    val before = store.execute(ComplexModel.getChanges(added.key, maxVersions = 10u))
                    supplied.value = "changed again"
                    assertEquals(before, store.execute(ComplexModel.getChanges(added.key, maxVersions = 10u)))
                }
            } finally { store.close() }
        }
    }

    @Test
    fun changingMutableTypedCellsKeepsCurrentAndHistoricSnapshotsStable() = runTest {
        for (history in listOf(false, true)) {
            val store = InMemoryDataStore.open(dataModelsById = mapOf(1u to ComplexModel), keepAllVersions = history)
            try {
                val original = ComplexModel.create(setDefaults = false) { }
                val added = assertIs<AddSuccess<ComplexModel>>(store.execute(ComplexModel.add(original)).statuses.single())
                val root = MutableTypedValue(T1, "committed root")
                val mapItem = MutableTypedValue(T1, "committed map item")
                val changed = assertIs<ChangeSuccess<ComplexModel>>(store.execute(ComplexModel.change(added.key.change(
                    Change(ComplexModel.ref { multi } with root, ComplexModel.ref { mapIntMulti } with mapOf(1u to mapItem)),
                ))).statuses.single())
                val snapshot = store.execute(ComplexModel.get(added.key)).values.single()
                val exported = store.execute(ComplexModel.getChanges(added.key, maxVersions = if (history) 10u else 1u))

                root.value = "external root mutation"
                mapItem.value = "external map mutation"
                val current = store.execute(ComplexModel.get(added.key)).values.single()
                assertEquals("committed root", current.values { multi }!!.value)
                assertEquals("committed map item", current.values { mapIntMulti }!!.getValue(1u).value)
                assertFalse(current.values { multi } is MutableTypedValue<*, *>)
                assertFalse(current.values { mapIntMulti }!!.getValue(1u) is MutableTypedValue<*, *>)
                assertEquals(changed.version, current.lastVersion)
                assertEquals(snapshot, current)
                assertEquals(exported, store.execute(ComplexModel.getChanges(added.key, maxVersions = if (history) 10u else 1u)))
                if (history) {
                    assertEquals(snapshot, store.execute(ComplexModel.get(added.key, toVersion = changed.version)).values.single())
                    assertEquals(original, store.execute(ComplexModel.get(added.key, toVersion = added.version)).values.single().values)
                }
            } finally { store.close() }
        }
    }
}
