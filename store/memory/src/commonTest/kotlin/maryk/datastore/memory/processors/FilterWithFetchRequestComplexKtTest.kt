package maryk.datastore.memory.processors

import maryk.core.clock.HLC
import maryk.core.models.IsRootValuesDataModel
import maryk.core.models.key
import maryk.core.processors.datastore.writeToStorage
import maryk.core.properties.IsValuesPropertyDefinitions
import maryk.core.properties.types.Key
import maryk.core.query.filters.Equals
import maryk.core.query.filters.Exists
import maryk.core.values.Values
import maryk.datastore.memory.records.DataRecord
import maryk.datastore.memory.records.DataRecordValue
import maryk.test.models.ComplexModel
import kotlin.test.Test
import kotlin.test.assertTrue

class FilterWithFetchRequestComplexKtTest {
    private val value1 = ComplexModel.Model.createDataRecord(
        ComplexModel(
            mapStringString = mapOf(
                "k1" to "v1",
                "k2" to "v2"
            )
        )
    )

    private val recordFetcher = { _: IsRootValuesDataModel<*>, _: Key<*> ->
        null
    }

    private fun <DM : IsRootValuesDataModel<P>, P : IsValuesPropertyDefinitions> DM.createDataRecord(values: Values<DM, P>): DataRecord<DM, P> {
        val recordValues = mutableListOf<DataRecordValue<*>>()

        values.writeToStorage { _, reference, _, value ->
            recordValues += DataRecordValue(reference, value, HLC(1234uL))
        }

        return DataRecord(
            key = this.key(values),
            firstVersion = HLC(1234uL),
            lastVersion = HLC(1234uL),
            values = recordValues
        )
    }

    @Test
    fun doExistsFilter() {
        assertTrue {
            filterMatches(
                Exists(ComplexModel { mapStringString.refAt("k1") }),
                value1,
                null,
                recordFetcher
            )
        }
    }

    @Test
    fun doEqualsFilter() {
        assertTrue {
            filterMatches(
                Equals(ComplexModel { mapStringString.refAt("k1") } with "v1"),
                value1,
                null,
                recordFetcher
            )
        }

        assertTrue {
            filterMatches(
                Equals(ComplexModel { mapStringString.refToAny() } with "v2"),
                value1,
                null,
                recordFetcher
            )
        }
    }
}
