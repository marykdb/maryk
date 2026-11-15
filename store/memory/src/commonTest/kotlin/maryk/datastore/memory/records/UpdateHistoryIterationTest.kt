package maryk.datastore.memory.records

import maryk.core.clock.HLC
import maryk.datastore.memory.records.DataStore.UpdateHistoryRecord
import maryk.lib.extensions.compare.compareTo
import maryk.test.models.SimpleMarykModel
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UpdateHistoryIterationTest {
    @Test
    fun lazyHistoryMatchesStableVersionAndUnsignedKeySortingWithCursorBounds() {
        val store = DataStore<SimpleMarykModel>(true, true)
        val random = Random(1234)
        repeat(500) { index ->
            store.addToUpdateHistory(HLC(random.nextInt(1, 10).toULong()),
                byteArrayOf(random.nextInt(256).toByte()), isHardDelete = index % 2 == 0)
        }
        // Force duplicate version/key entries to check stable same-key ordering.
        store.addToUpdateHistory(HLC(5uL), byteArrayOf(127), false)
        store.addToUpdateHistory(HLC(5uL), byteArrayOf(127), true)
        val sorted = store.updateHistory.sortedWith(
            compareByDescending<UpdateHistoryRecord> { it.version }
                .thenComparator { first, second -> first.keyBytes compareTo second.keyBytes },
        )
        for (from in listOf(0uL, 4uL, 11uL)) {
            for (to in listOf(0uL, 5uL, ULong.MAX_VALUE)) {
                for (after in listOf(null, byteArrayOf(0), byteArrayOf(127), byteArrayOf(255.toByte()))) {
                    val expected = sorted.filter {
                        it.version in from..to && (it.version != to || after == null || it.keyBytes > after)
                    }
                    assertEquals(expected, store.updateHistory.scanUpdateHistory(from, to, after).toList())
                }
            }
        }
    }

    @Test
    fun firstPageSeeksDirectlyWithoutMaterializingTheFullIndex() {
        var probes = 0
        val history = object : AbstractList<UpdateHistoryRecord>() {
            override val size = 1_000_000
            override fun get(index: Int): UpdateHistoryRecord {
                probes++
                return UpdateHistoryRecord((size - index).toULong(), byteArrayOf(1))
            }
        }
        val page = history.scanUpdateHistory(0uL, 500_000uL).take(2).toList()
        assertEquals(listOf(500_000uL, 499_999uL), page.map { it.version })
        assertTrue(probes < 50, "History page inspected $probes entries")
    }
}
