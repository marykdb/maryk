package maryk.datastore.memory.records

import maryk.core.clock.HLC
import maryk.core.models.IsRootDataModel
import maryk.core.models.RootDataModel
import maryk.core.processors.datastore.writeToStorage
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.IsSimpleValueDefinition
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.definitions.map
import maryk.core.properties.definitions.set
import maryk.core.values.Values
import maryk.core.models.key
import kotlin.test.Test
import kotlin.test.assertFailsWith

private object ThrowingStringDefinition : IsSimpleValueDefinition<String, IsPropertyContext> by StringDefinition() {
    override fun readStorageBytes(length: Int, reader: () -> Byte): String {
        throw IllegalStateException("boom")
    }
}

private object ThrowingRecordModel : RootDataModel<ThrowingRecordModel>() {
    val map by map(
        index = 1u,
        required = false,
        keyDefinition = ThrowingStringDefinition,
        valueDefinition = StringDefinition()
    )

    val set by set(
        index = 2u,
        required = false,
        valueDefinition = ThrowingStringDefinition
    )
}

internal class DataRecordImplementationFailureTest {
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
    fun mapReadPropagatesImplementationFailure() {
        val record = ThrowingRecordModel.createDataRecord(
            ThrowingRecordModel.create {
                map with mapOf("good" to "value")
            }
        )

        assertFailsWith<IllegalStateException> {
            record.get(ThrowingRecordModel { map::ref })
        }
    }

    @Test
    fun mapFuzzyReadPropagatesImplementationFailure() {
        val record = ThrowingRecordModel.createDataRecord(
            ThrowingRecordModel.create {
                map with mapOf("good" to "value")
            }
        )

        assertFailsWith<IllegalStateException> {
            record.matchQualifier(
                reference = ThrowingRecordModel { map.refToAnyKey() },
                toVersion = null,
                recordFetcher = { _, _ -> null }
            ) { true }
        }
    }

    @Test
    fun setReadPropagatesImplementationFailure() {
        val record = ThrowingRecordModel.createDataRecord(
            ThrowingRecordModel.create {
                set with setOf("good")
            }
        )

        assertFailsWith<IllegalStateException> {
            record.get(ThrowingRecordModel { set::ref })
        }
    }

    @Test
    fun setFuzzyReadPropagatesImplementationFailure() {
        val record = ThrowingRecordModel.createDataRecord(
            ThrowingRecordModel.create {
                set with setOf("good")
            }
        )

        assertFailsWith<IllegalStateException> {
            record.matchQualifier(
                reference = ThrowingRecordModel { set.refToAny() },
                toVersion = null,
                recordFetcher = { _, _ -> null }
            ) { true }
        }
    }
}
