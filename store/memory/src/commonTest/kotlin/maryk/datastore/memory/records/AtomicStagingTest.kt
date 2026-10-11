package maryk.datastore.memory.records

import maryk.core.clock.HLC
import maryk.core.models.key
import maryk.core.properties.types.Bytes
import maryk.datastore.memory.records.index.UniqueIndexValues
import maryk.datastore.shared.AtomicAbort
import maryk.datastore.shared.MAX_ATOMIC_STAGED_NODES
import maryk.datastore.shared.rejectAtomicLimit
import maryk.test.models.SimpleMarykModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertNull

class AtomicStagingTest {
    private val reference = SimpleMarykModel.value.ref().toStorageByteArray()
    private fun record(value: String, historySize: Int = 0): DataRecord<SimpleMarykModel> {
        val key = SimpleMarykModel.key(SimpleMarykModel.create { this.value with value })
        val node = if (historySize == 0) DataRecordValue(reference, value, HLC(1uL)) else
            DataRecordHistoricValues(reference, List(historySize) { DataRecordValue(reference, value, HLC(it.toULong() + 1u)) })
        return DataRecord(key, listOf(node), HLC(1uL), HLC(1uL))
    }
    @Suppress("UNCHECKED_CAST")
    private fun index(store: DataStore<SimpleMarykModel>) = store.getOrCreateUniqueIndex(reference) as UniqueIndexValues<SimpleMarykModel, String>
    private fun budget(): (Int) -> Unit {
        var nodes = 0L
        return { count ->
            nodes += count
            if (nodes > MAX_ATOMIC_STAGED_NODES) rejectAtomicLimit("stagedNodes", MAX_ATOMIC_STAGED_NODES.toULong(), nodes.toULong())
        }
    }

    @Test
    fun smallChangeCanStageBesideLargeUntouchedHistory() {
        val store = DataStore<SimpleMarykModel>(true, true)
        val target = record("haha-target", 2)
        val unrelated = record("haha-unrelated", 100_001)
        store.records.addAll(listOf(target, unrelated).sortedBy { Bytes(it.key.bytes) })
        val index = index(store)
        index.addToIndex(target, "target", HLC(1uL))
        val staged = store.copyForAtomic(setOf(Bytes(target.key.bytes)), budget())
        val changed = staged.getByKey(target.key.bytes)!!
        assertNotSame(target, changed)
        assertSame(unrelated, staged.getByKey(unrelated.key.bytes))
        assertSame(changed, index(staged)["target"])
        @Suppress("UNCHECKED_CAST")
        val history = changed.values.single() as DataRecordHistoricValues<String>
        history.add(DataRecordValue(reference, "haha-changed", HLC(3uL)))
        history.commit()
        assertEquals(2, (target.values.single() as DataRecordHistoricValues<*>).history.size)
        assertEquals(3, history.history.size)
    }

    @Test
    fun targetedLargeHistoryReportsItsBudgetWithoutMutatingOriginal() {
        val store = DataStore<SimpleMarykModel>(true, false)
        val target = record("haha-target", 100_001)
        store.records += target
        val rejection = assertFailsWith<AtomicAbort> { store.copyForAtomic(setOf(Bytes(target.key.bytes)), budget()) }
        assertEquals("stagedNodes", rejection.response.failure?.limit)
        assertEquals(100_000uL, rejection.response.failure?.maximum)
        assertSame(target, store.records.single())
    }

    @Test
    fun claimingAnUnownedHistoricalIndexNeverChangesOriginalAndDetachesAfterCommit() {
        val store = DataStore<SimpleMarykModel>(true, false)
        val target = record("haha-target")
        val old = record("haha-old")
        store.records.addAll(listOf(target, old).sortedBy { Bytes(it.key.bytes) })
        val index = index(store)
        index.addToIndex(old, "free", HLC(1uL))
        index.removeFromIndex(old, "free", HLC(2uL), true)
        var finished = false
        val staged = store.copyForAtomic(setOf(Bytes(target.key.bytes))) { check(!finished) }
        val stagedIndex = index(staged)
        val changed = staged.getByKey(target.key.bytes)!!
        stagedIndex.addToIndex(changed, "free", HLC(3uL))
        assertNull(index["free"])
        assertSame(old, index["free", HLC(1uL)])
        staged.finishAtomicCopy()
        finished = true
        stagedIndex.removeFromIndex(changed, "free", HLC(4uL), true)
        assertNull(stagedIndex["free"])
        assertSame(changed, stagedIndex["free", HLC(3uL)])
        assertNull(index["free"])
    }
}
