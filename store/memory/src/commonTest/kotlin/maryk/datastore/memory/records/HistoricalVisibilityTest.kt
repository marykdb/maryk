package maryk.datastore.memory.records

import maryk.core.clock.HLC
import maryk.core.properties.types.Key
import maryk.test.models.SimpleMarykModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class HistoricalVisibilityTest {
    private fun record(keyByte: Int, version: ULong) = DataRecord<SimpleMarykModel>(
        Key(byteArrayOf(keyByte.toByte())), emptyList(), HLC(version), HLC(version),
    )

    @Test
    fun currentOnlyHistoryExcludesLaterCreationsAndKeepsUnsignedKeyOrder() {
        val store = DataStore<SimpleMarykModel>(true, false)
        val first = record(1, 1uL)
        val later = record(127, 3uL)
        val last = record(255, 2uL)
        store.records.addAll(listOf(first, later, last))

        assertSame(store.records, store.getRecordsAtVersion(null))
        assertEquals(emptyList(), store.getRecordsAtVersion(HLC(0uL)))
        assertEquals(listOf(first), store.getRecordsAtVersion(HLC(1uL)))
        assertEquals(listOf(first, last), store.getRecordsAtVersion(HLC(2uL)))
        assertEquals(listOf(first, later, last), store.getRecordsAtVersion(HLC(3uL)))
        assertEquals(listOf(first, later, last), store.records)
    }

    @Test
    fun archivedIncarnationsMergeInKeyOrderOnlyInsideTheirVisibilityWindow() {
        val store = DataStore<SimpleMarykModel>(true, false)
        val oldest = record(127, 1uL)
        val previous = record(127, 3uL)
        val current = record(127, 5uL)
        val first = record(1, 1uL)
        val last = record(255, 1uL)
        store.records.addAll(listOf(first, current))
        store.hardDeletedRecords.addAll(listOf(
            DataStore.HardDeletedRecord(oldest, HLC(2uL)),
            DataStore.HardDeletedRecord(last, HLC(6uL)),
            DataStore.HardDeletedRecord(previous, HLC(4uL)),
        ))

        assertEquals(listOf(first, oldest, last), store.getRecordsAtVersion(HLC(1uL)))
        assertEquals(listOf(first, last), store.getRecordsAtVersion(HLC(2uL)))
        assertEquals(listOf(first, previous, last), store.getRecordsAtVersion(HLC(3uL)))
        assertEquals(listOf(first, last), store.getRecordsAtVersion(HLC(4uL)))
        assertEquals(listOf(first, current, last), store.getRecordsAtVersion(HLC(5uL)))
        assertEquals(listOf(first, current), store.getRecordsAtVersion(HLC(6uL)))
        assertEquals(listOf(first, current), store.records)
    }
}
