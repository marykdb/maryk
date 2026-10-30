package maryk.datastore.memory

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import maryk.core.properties.types.MutableTypedValue
import maryk.core.properties.types.invoke
import maryk.core.query.changes.Change
import maryk.core.query.changes.IncMapChange
import maryk.core.query.changes.change
import maryk.core.query.pairs.with
import maryk.core.query.requests.add
import maryk.core.query.requests.change
import maryk.core.query.requests.get
import maryk.core.query.responses.UpdateResponse
import maryk.core.query.responses.statuses.AddSuccess
import maryk.core.query.responses.statuses.ChangeSuccess
import maryk.core.query.responses.updates.AdditionUpdate
import maryk.core.query.responses.updates.ChangeUpdate
import maryk.core.values.Values
import maryk.datastore.shared.updates.FlowUpdate
import maryk.datastore.shared.updates.Update
import maryk.test.models.ComplexModel
import maryk.test.models.EmbeddedMarykModel
import maryk.test.models.MarykTypeEnum.T1
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class UpdateOperandOwnershipTest {
    @Test
    fun emittedAdditionOwnsItsOriginalValuesAndNestedOperands() = runTest {
        val source = InMemoryDataStore.open(dataModelsById = mapOf(1u to ComplexModel), keepAllVersions = true)
        val replica = InMemoryDataStore.open(dataModelsById = mapOf(1u to ComplexModel), keepAllVersions = true)
        try {
            val typed = MutableTypedValue(T1, "original typed item")
            val list = mutableListOf("original list item")
            val original = ComplexModel.create(setDefaults = false) {
                multi with T1("original root")
                mapIntMulti with mapOf(1u to typed)
                mapWithList with mapOf("items" to list)
            }
            val pending = async(start = CoroutineStart.UNDISPATCHED) {
                source.updateSharedFlow.filterIsInstance<FlowUpdate>().first { it.update is Update.Addition }
            }
            val added = assertIs<AddSuccess<ComplexModel>>(source.execute(ComplexModel.add(original)).statuses.single())
            val event = assertIs<Update.Addition<*>>(pending.await().update)
            val committed = source.execute(ComplexModel.get(added.key)).values.single()

            original.mutate { arrayOf(multi asValueItem T1("changed caller root")) }
            typed.value = "changed caller typed item"
            list += "changed caller list item"

            @Suppress("UNCHECKED_CAST")
            val values = event.values as Values<ComplexModel>
            replica.processUpdate(UpdateResponse(ComplexModel,
                AdditionUpdate(added.key, event.version, event.version, 0, event.isDeleted, values)))
            assertEquals(committed, replica.execute(ComplexModel.get(added.key)).values.single())
        } finally { replica.close(); source.close() }
    }

    @Test
    fun emittedChangesKeepOperandsStableForDeferredReplication() = runTest {
        val source = InMemoryDataStore.open(dataModelsById = mapOf(1u to ComplexModel), keepAllVersions = true)
        val replica = InMemoryDataStore.open(dataModelsById = mapOf(1u to ComplexModel), keepAllVersions = true)
        try {
            val original = ComplexModel.create(setDefaults = false) { multi with T1("initial") }
            val added = assertIs<AddSuccess<ComplexModel>>(source.execute(ComplexModel.add(original)).statuses.single())
            replica.processUpdate(UpdateResponse(ComplexModel,
                AdditionUpdate(added.key, added.version, added.version, 0, false, original)))
            val typed = MutableTypedValue(T1, "committed root")
            val list = mutableListOf("committed list item")
            val embedded = EmbeddedMarykModel.create(setDefaults = false) { value with "committed allocation" }
            val allocations = mutableListOf(embedded)
            val pending = async(start = CoroutineStart.UNDISPATCHED) {
                source.updateSharedFlow.filterIsInstance<FlowUpdate>().first { it.update is Update.Change }
            }
            val changed = assertIs<ChangeSuccess<ComplexModel>>(source.execute(ComplexModel.change(added.key.change(
                Change(ComplexModel.ref { multi } with typed, ComplexModel.ref { mapWithList } with mapOf("items" to list)),
                IncMapChange(ComplexModel.ref { incMap }.change(addValues = allocations)),
            ))).statuses.single())
            val event = assertIs<Update.Change<*>>(pending.await().update)
            val committed = source.execute(ComplexModel.get(added.key)).values.single()

            typed.value = "changed caller root"
            list += "changed caller list item"
            embedded.mutate { arrayOf(value asValueItem "changed caller allocation") }
            allocations.clear()

            replica.processUpdate(UpdateResponse(ComplexModel,
                ChangeUpdate(added.key, event.version, 0, event.changes)))
            assertEquals(changed.version, event.version)
            assertEquals(committed, replica.execute(ComplexModel.get(added.key)).values.single())
        } finally { replica.close(); source.close() }
    }
}
