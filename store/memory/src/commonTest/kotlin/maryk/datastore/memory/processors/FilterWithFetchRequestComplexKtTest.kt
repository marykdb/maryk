package maryk.datastore.memory.processors

import maryk.core.models.IsRootValuesDataModel
import maryk.core.models.key
import maryk.core.processors.datastore.writeToStorage
import maryk.core.properties.PropertyDefinitions
import maryk.core.query.filters.Equals
import maryk.core.query.filters.Exists
import maryk.core.values.Values
import maryk.datastore.memory.records.DataRecord
import maryk.datastore.memory.records.DataRecordValue
import maryk.test.models.ComplexModel
import maryk.test.shouldBe
import kotlin.test.Test

class FilterWithFetchRequestComplexKtTest {
    private val value1 = ComplexModel.createDataRecord(
        ComplexModel(
            mapStringString = mapOf(
                "k1" to "v1",
                "k2" to "v2"
            )
        )
    )

    private fun <DM : IsRootValuesDataModel<P>, P : PropertyDefinitions> DM.createDataRecord(values: Values<DM, P>): DataRecord<DM, P> {
        val recordValues = mutableListOf<DataRecordValue<*>>()

        values.writeToStorage { _, reference, _, value ->
            recordValues += DataRecordValue(reference, value, 1234uL)
        }

        return DataRecord(
            key = this.key(values),
            firstVersion = 1234uL,
            lastVersion = 1234uL,
            values = recordValues
        )
    }

    @Test
    fun doExistsFilter() {
        filterMatches(
            Exists(ComplexModel { mapStringString.refAt("k1") }),
            value1,
            null
        ) shouldBe true

        filterMatches(
            Exists(ComplexModel { mapStringString.refToAny() }),
            value1,
            null
        ) shouldBe true
    }

    @Test
    fun doEqualsFilter() {
        filterMatches(
            Equals(ComplexModel { mapStringString.refAt("k1") } with "v1"),
            value1,
            null
        ) shouldBe true

        filterMatches(
            Equals(ComplexModel { mapStringString.refToAny() } with "v2"),
            value1,
            null
        ) shouldBe true
    }
}