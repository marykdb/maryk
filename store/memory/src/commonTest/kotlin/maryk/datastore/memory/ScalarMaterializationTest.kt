package maryk.datastore.memory

import maryk.core.clock.HLC
import maryk.core.models.RootDataModel
import maryk.core.models.graph
import maryk.core.models.key
import maryk.core.properties.definitions.string
import maryk.datastore.memory.processors.objectSoftDeleteQualifier
import maryk.datastore.memory.processors.recordToValueWithMeta
import maryk.datastore.memory.records.DataRecord
import maryk.datastore.memory.records.DataRecordHistoricValues
import maryk.datastore.memory.records.DataRecordValue
import maryk.datastore.memory.records.DeletedValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

private object ScalarModel : RootDataModel<ScalarModel>() {
    val first by string(1u)
    val optional by string(2u, required = false)
    val last by string(3u)
    val far by string(33u, required = false)
}

class ScalarMaterializationTest {
    @Test
    fun currentScalarsMatchGeneralReaderIncludingProjectionAndDeletedProperties() {
        val initial = HLC(1uL)
        val changed = HLC(3uL)
        val firstQualifier = ScalarModel.ref { first }.toStorageByteArray()
        val optionalQualifier = ScalarModel.ref { optional }.toStorageByteArray()
        val lastQualifier = ScalarModel.ref { last }.toStorageByteArray()
        val record = DataRecord(
            ScalarModel.key(ByteArray(ScalarModel.Meta.keyByteSize)),
            listOf(
                DataRecordHistoricValues(firstQualifier, listOf(
                    DataRecordValue(firstQualifier, "before", initial), DataRecordValue(firstQualifier, "after", changed),
                )),
                DataRecordHistoricValues(optionalQualifier, listOf(
                    DataRecordValue(optionalQualifier, "removed", initial), DeletedValue<String>(optionalQualifier, changed),
                )),
                DataRecordValue(lastQualifier, "last", initial),
            ), initial, changed,
        )
        // An explicit object-delete qualifier takes the general reader without changing visible state.
        val general = record.copy(values = listOf(DataRecordValue(objectSoftDeleteQualifier, false, initial)) + record.values)
        val projections = listOf(null, ScalarModel.graph { listOf(last, first) },
            ScalarModel.graph { listOf(optional) }, ScalarModel.graph { emptyList() },
            ScalarModel.graph { listOf(far) }, ScalarModel.graph { listOf(far, first) })
        for (select in projections) {
            val actual = assertNotNull(ScalarModel.recordToValueWithMeta(select, null, record))
            val expected = assertNotNull(ScalarModel.recordToValueWithMeta(select, null, general))
            assertEquals(expected, actual)
        }
        assertEquals("before", assertNotNull(ScalarModel.recordToValueWithMeta(null, initial, record)).values { first })
        assertEquals("removed", assertNotNull(ScalarModel.recordToValueWithMeta(null, initial, record)).values { optional })
    }
}
