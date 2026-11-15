package maryk.datastore.memory

import maryk.core.clock.HLC
import maryk.core.models.RootDataModel
import maryk.core.processors.datastore.writeToStorage
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.definitions.list
import maryk.core.properties.definitions.number
import maryk.core.properties.definitions.string
import maryk.core.properties.references.dsl.at
import maryk.core.properties.types.Key
import maryk.core.properties.types.numeric.SInt32
import maryk.datastore.memory.processors.readCurrentRootStringCollections
import maryk.datastore.memory.processors.recordToValueWithMeta
import maryk.datastore.memory.processors.objectSoftDeleteQualifier
import maryk.datastore.memory.records.DataRecord
import maryk.datastore.memory.records.DataRecordHistoricValues
import maryk.datastore.memory.records.DataRecordNode
import maryk.datastore.memory.records.DataRecordValue
import maryk.datastore.memory.records.DeletedValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

private object CurrentListModel : RootDataModel<CurrentListModel>() {
    val title by string(1u)
    val entries by list(2u, valueDefinition = StringDefinition())
    val amount by number(3u, type = SInt32)
}

private object WideListModel : RootDataModel<WideListModel>() {
    val title by string(1u)
    val entries by list(16u, valueDefinition = StringDefinition())
}

class CurrentRootStringListReadTest {
    @Test
    fun softDeleteMarkersPreserveTheirStateAndVersion() {
        for (deleted in listOf(true, false)) {
            val record = record()
            (record.values as MutableList<DataRecordNode>).add(0, DataRecordValue(objectSoftDeleteQualifier, deleted, HLC(6uL)))
            assertEquals(HLC(6uL), record.currentVersion())
            assertEquals(
                CurrentListModel.recordToValueWithMeta(null, HLC(6uL), record),
                CurrentListModel.readCurrentRootStringCollections(record),
            )
        }
    }

    private fun record(): DataRecord<CurrentListModel> {
        val nodes = ArrayList<DataRecordNode>()
        CurrentListModel.create {
            title with "title"; entries with List(32) { "item-$it" }; amount with 42
        }.writeToStorage { _, reference, _, value -> nodes += DataRecordValue(reference, value, HLC(1uL)) }
        val count = CurrentListModel.entries.ref().toStorageByteArray()
        val first = CurrentListModel.ref { entries.at(0u) }.toStorageByteArray()
        val title = CurrentListModel.title.ref().toStorageByteArray()
        for (index in nodes.indices) {
            val node = nodes[index]
            when {
                node.reference.contentEquals(count) -> nodes[index] = DataRecordHistoricValues(count, listOf(
                    DataRecordValue(count, 33, HLC(1uL)), DataRecordValue(count, 32, HLC(5uL)),
                ))
                node.reference.contentEquals(first) -> nodes[index] = DataRecordHistoricValues(first, listOf(
                    DataRecordValue(first, "item-0", HLC(1uL)), DataRecordValue(first, "after", HLC(3uL)),
                ))
                node.reference.contentEquals(title) -> nodes[index] = DataRecordValue(title, "title", HLC(5uL))
            }
        }
        val removed = CurrentListModel.ref { entries.at(32u) }.toStorageByteArray()
        nodes.add(nodes.indexOfFirst { it.reference.contentEquals(CurrentListModel.amount.ref().toStorageByteArray()) },
            DataRecordHistoricValues(removed, listOf(
                DataRecordValue(removed, "removed", HLC(1uL)), DeletedValue<String>(removed, HLC(5uL)),
            )))
        return DataRecord(Key(byteArrayOf(1)), nodes, HLC(1uL), HLC(5uL))
    }

    @Test
    fun currentListsMatchTheHistoricDecoderIncludingTombstonesAndOwnTheirContainers() {
        val record = record()
        record.lastVersion = HLC(1uL)
        val current = assertNotNull(CurrentListModel.readCurrentRootStringCollections(record))
        val generic = assertNotNull(CurrentListModel.recordToValueWithMeta(null, HLC(5uL), record))
        assertEquals(generic.lastVersion, record.currentVersion().timestamp)
        assertEquals(generic, current)
        assertEquals(32, current.values { entries }?.size)
        assertEquals("after", current.values { entries }?.first())
        (current.values { entries } as MutableList<String>)[0] = "caller-owned"
        assertEquals("after", CurrentListModel.recordToValueWithMeta(null, null, record)?.values { entries }?.first())
    }

    @Test
    fun missingPositionsFallBackInsteadOfReturningATruncatedList() {
        val record = record()
        val missing = CurrentListModel.ref { entries.at(8u) }.toStorageByteArray()
        (record.values as MutableList<DataRecordNode>).removeAll { it.reference.contentEquals(missing) }
        assertNull(CurrentListModel.readCurrentRootStringCollections(record))
        assertFailsWith<IndexOutOfBoundsException> { CurrentListModel.recordToValueWithMeta(null, null, record) }
    }

    @Test
    fun widerRootQualifiersKeepTheGeneralDecoder() {
        val nodes = ArrayList<DataRecordNode>()
        val expected = WideListModel.create { title with "title"; entries with List(32) { "item-$it" } }
        expected.writeToStorage { _, reference, _, value -> nodes += DataRecordValue(reference, value, HLC(1uL)) }
        val record = DataRecord<WideListModel>(Key(byteArrayOf(1)), nodes, HLC(1uL), HLC(1uL))
        assertNull(WideListModel.readCurrentRootStringCollections(record))
        assertEquals(expected, WideListModel.recordToValueWithMeta(null, null, record)?.values)
    }
}
