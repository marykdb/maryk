package maryk.datastore.memory

import maryk.core.clock.HLC
import maryk.core.models.RootDataModel
import maryk.core.processors.datastore.writeToStorage
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.definitions.map
import maryk.core.properties.definitions.string
import maryk.core.properties.types.Key
import maryk.core.query.pairs.with
import maryk.datastore.memory.processors.readCurrentRootStringCollections
import maryk.datastore.memory.processors.recordToValueWithMeta
import maryk.datastore.memory.records.DataRecord
import maryk.datastore.memory.records.DataRecordHistoricValues
import maryk.datastore.memory.records.DataRecordNode
import maryk.datastore.memory.records.DataRecordValue
import maryk.datastore.memory.records.DeletedValue
import maryk.lib.extensions.compare.compareTo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

private object CurrentMapModel : RootDataModel<CurrentMapModel>() {
    val title by string(1u)
    val labels by map(2u, keyDefinition = StringDefinition(), valueDefinition = StringDefinition())
}

class CurrentRootStringMapReadTest {
    @Test
    fun mapReadsOwnContainersAndPreserveUnicodeKeysHistoryAndTombstones() {
        val labels = (0 until 40).associate { "key-$it" to "value-$it" } + mapOf("" to "empty", "é🙂" to "unicode", "x".repeat(140) to "long")
        val nodes = ArrayList<DataRecordNode>()
        CurrentMapModel.create { title with "title"; CurrentMapModel.labels with labels }.writeToStorage { _, qualifier, _, value ->
            nodes += DataRecordValue(qualifier, value, HLC(1uL))
        }
        val count = CurrentMapModel.labels.ref().toStorageByteArray()
        for (index in nodes.indices) {
            val node = nodes[index] as DataRecordValue<*>
            when {
                node.reference.contentEquals(count) -> nodes[index] = DataRecordHistoricValues(count, listOf(
                    DataRecordValue(count, labels.size, HLC(1uL)), DataRecordValue(count, labels.size - 1, HLC(3uL)),
                ))
                node.value == "value-0" -> nodes[index] = DataRecordHistoricValues(node.reference, listOf(
                    DataRecordValue(node.reference, "value-0", HLC(1uL)), DataRecordValue(node.reference, "changed", HLC(3uL)),
                ))
                node.value == "value-1" -> nodes[index] = DataRecordHistoricValues(node.reference, listOf(
                    DataRecordValue(node.reference, "value-1", HLC(1uL)), DeletedValue<String>(node.reference, HLC(3uL)),
                ))
            }
        }
        nodes.sortWith { left, right -> left.reference compareTo right.reference }
        val record = DataRecord(Key<CurrentMapModel>(ByteArray(16)), nodes, HLC(1uL), HLC(1uL))
        val result = assertNotNull(CurrentMapModel.readCurrentRootStringCollections(record))
        assertEquals(CurrentMapModel.recordToValueWithMeta(null, HLC(3uL), record), result)
        assertEquals(labels - "key-1" + ("key-0" to "changed"), result.values { CurrentMapModel.labels })
        @Suppress("UNCHECKED_CAST")
        (result.values { CurrentMapModel.labels } as MutableMap<String, String>)["key-0"] = "caller mutation"
        assertEquals("changed", CurrentMapModel.readCurrentRootStringCollections(record)?.values { CurrentMapModel.labels }?.get("key-0"))
    }

    @Test
    fun emptyMapsWorkAndInvalidCountsDeclineMaterialization() {
        val nodes = ArrayList<DataRecordNode>()
        CurrentMapModel.create { title with "title"; labels with emptyMap() }.writeToStorage { _, qualifier, _, value ->
            nodes += DataRecordValue(qualifier, value, HLC(1uL))
        }
        val record = DataRecord(Key<CurrentMapModel>(ByteArray(16)), nodes, HLC(1uL), HLC(1uL))
        assertEquals(emptyMap(), CurrentMapModel.readCurrentRootStringCollections(record)?.values { labels })
        val countIndex = nodes.indexOfFirst { it.reference.contentEquals(CurrentMapModel.labels.ref().toStorageByteArray()) }
        nodes[countIndex] = DataRecordValue(nodes[countIndex].reference, Int.MAX_VALUE, HLC(1uL))
        assertNull(CurrentMapModel.readCurrentRootStringCollections(record))
    }
}
