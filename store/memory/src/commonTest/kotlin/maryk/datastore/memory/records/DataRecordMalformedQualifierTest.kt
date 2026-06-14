package maryk.datastore.memory.records

import kotlinx.datetime.LocalDate
import maryk.core.clock.HLC
import maryk.core.models.IsRootDataModel
import maryk.core.models.key
import maryk.core.processors.datastore.writeToStorage
import maryk.core.values.Values
import maryk.test.models.ComplexModel
import maryk.test.models.TestMarykModel
import kotlin.test.Test
import kotlin.test.assertEquals

class DataRecordMalformedQualifierTest {
    private fun <DM : IsRootDataModel> DM.createDataRecord(values: Values<DM>): DataRecord<DM> {
        val recordValues = mutableListOf<DataRecordValue<*>>()

        values.writeToStorage { _, reference, _, value ->
            recordValues += DataRecordValue(reference, value, HLC(1234uL))
        }

        return DataRecord(
            key = this.key(values),
            firstVersion = HLC(1234uL),
            lastVersion = HLC(1234uL),
            values = recordValues,
        )
    }

    @Test
    fun mapReadSkipsMalformedQualifier() {
        val record = ComplexModel.createDataRecord(
            ComplexModel.create {
                mapStringString with mapOf("good" to "value")
            }
        )
        val malformedReference = ComplexModel { mapStringString::ref }.toStorageByteArray() + byteArrayOf(-128)
        val corrupted = record.copy(
            values = record.values + DataRecordValue(malformedReference, "broken", HLC(1235uL))
        )

        assertEquals(
            mapOf("good" to "value"),
            corrupted.get(ComplexModel { mapStringString::ref }),
        )
    }

    @Test
    fun setReadSkipsMalformedQualifier() {
        val record = TestMarykModel.createDataRecord(
            TestMarykModel.create {
                set with setOf(LocalDate(2018, 9, 9))
            }
        )
        val malformedReference = TestMarykModel { set::ref }.toStorageByteArray() + byteArrayOf(-128)
        val corrupted = record.copy(
            values = record.values + DataRecordValue(malformedReference, LocalDate(2019, 1, 1), HLC(1235uL))
        )

        assertEquals(
            setOf(LocalDate(2018, 9, 9)),
            corrupted.get(TestMarykModel { set::ref }),
        )
    }
}
