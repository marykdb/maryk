package maryk.datastore.memory.records

import maryk.core.clock.HLC
import maryk.core.models.IsRootDataModel
import maryk.core.models.key
import maryk.core.processors.datastore.writeToStorage
import maryk.core.values.Values
import maryk.lib.extensions.compare.compareTo
import maryk.test.models.AnyValueMapIndexModel
import maryk.test.models.AnyValueSetIndexModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CollectionRangeReadTest {
    private fun <DM : IsRootDataModel> checkedRecord(model: DM, values: Values<DM>, check: (DataRecord<DM>) -> Unit) {
        val nodes = mutableListOf<DataRecordNode>()
        values.writeToStorage { _, reference, _, value -> nodes += DataRecordValue(reference, value, HLC(1uL)) }
        repeat(10_000) { index ->
            nodes += DataRecordValue(byteArrayOf(127, (index ushr 8).toByte(), index.toByte()), "unrelated", HLC(1uL))
        }
        nodes.sortWith { first, second -> first.reference compareTo second.reference }
        var probes = 0
        val counted = object : AbstractList<DataRecordNode>() {
            override val size get() = nodes.size
            override fun get(index: Int): DataRecordNode = nodes[index].also { probes++ }
        }
        check(DataRecord(model.key(values), counted, HLC(1uL), HLC(1uL)))
        assertTrue(probes < 50, "Collection lookup inspected $probes cells")
    }

    @Test
    fun mapIndexReadsOnlyItsQualifierRange() {
        val values = AnyValueMapIndexModel.create {
            name with "record"
            mapValues with mapOf("key" to "value")
        }
        checkedRecord(AnyValueMapIndexModel, values) {
            assertEquals(mapOf("key" to "value"), it[AnyValueMapIndexModel.mapValues.ref()])
        }
    }

    @Test
    fun setIndexReadsOnlyItsQualifierRange() {
        val values = AnyValueSetIndexModel.create {
            name with "record"
            setValues with setOf("value")
        }
        checkedRecord(AnyValueSetIndexModel, values) {
            assertEquals(setOf("value"), it[AnyValueSetIndexModel.setValues.ref()])
        }
    }
}
