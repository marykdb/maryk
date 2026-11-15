package maryk.datastore.memory.records

import maryk.core.clock.HLC
import maryk.datastore.memory.processors.changers.getValueAtIndex
import maryk.datastore.memory.processors.recordToValueWithMeta
import maryk.test.models.SimpleMarykModel
import maryk.core.properties.types.Key
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HistoryLookupTest {
    @Test
    fun historicReadsKeepLastEqualVersionDeletionAndPendingWriteSemantics() {
        val qualifier = SimpleMarykModel.value.ref().toStorageByteArray()
        val history = DataRecordHistoricValues(qualifier, listOf(
            DataRecordValue(qualifier, "ha old", HLC(1uL)),
            DeletedValue<String>(qualifier, HLC(2uL)),
            DataRecordValue(qualifier, "ha replaced", HLC(3uL)),
            DataRecordValue(qualifier, "ha same version", HLC(3uL)),
            DataRecordValue(qualifier, "ha latest", HLC(5uL)),
        ))
        history.add(DataRecordValue(qualifier, "ha pending", HLC(6uL)))
        val nodes = listOf(history)
        val record = DataRecord<SimpleMarykModel>(
            Key(ByteArray(SimpleMarykModel.Meta.keyByteSize)), nodes, HLC(1uL), HLC(5uL),
        )
        assertNull(getValueAtIndex<String>(nodes, 0, HLC(0uL)))
        assertEquals("ha old", getValueAtIndex<String>(nodes, 0, HLC(1uL))?.value)
        assertNull(getValueAtIndex<String>(nodes, 0, HLC(2uL)))
        assertEquals("ha same version", getValueAtIndex<String>(nodes, 0, HLC(3uL))?.value)
        assertEquals("ha same version", getValueAtIndex<String>(nodes, 0, HLC(4uL))?.value)
        assertEquals("ha latest", getValueAtIndex<String>(nodes, 0, HLC(6uL))?.value)
        assertEquals("ha pending", getValueAtIndex<String>(nodes, 0)?.value)
        val values = SimpleMarykModel.recordToValueWithMeta(null, HLC(3uL), record)!!
        assertEquals("ha same version", values.values { value })
        assertEquals(3uL, values.lastVersion)
    }

    @Test
    fun oldVersionLookupDoesNotWalkTheRetainedHistory() {
        var probes = 0
        val versions = object : AbstractList<HLC>() {
            override val size = 1_000_000
            override fun get(index: Int): HLC {
                probes++
                return HLC(index.toULong() * 2uL)
            }
        }
        assertEquals(1, versions.lastVersionIndex(HLC(3uL)) { it })
        assertTrue(probes < 25, "Historical lookup made $probes probes")
        assertEquals(-1, emptyList<HLC>().lastVersionIndex(HLC(0uL)) { it })
    }
}
