package maryk.datastore.memory

import kotlinx.coroutines.test.runTest
import maryk.core.clock.HLC
import maryk.core.models.IsRootDataModel
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.enum.invoke
import maryk.core.properties.references.AnyPropertyReference
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.references.dsl.atType
import maryk.core.properties.types.Key
import maryk.core.properties.types.invoke
import maryk.core.values.Values
import maryk.core.processors.datastore.writeToStorage
import maryk.datastore.memory.records.DataRecord
import maryk.datastore.test.StructuralFilterSource
import maryk.datastore.test.assertStructuralFilterEquivalence
import maryk.datastore.memory.records.DataRecordHistoricValues
import maryk.datastore.memory.records.DataRecordValue
import maryk.lib.extensions.compare.compareTo
import maryk.test.models.ComplexModel
import maryk.test.models.MarykTypeEnum.T1
import maryk.test.models.MarykTypeEnum.T3
import maryk.test.models.MarykTypeEnum.T4
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RetainedTypedBranchTest {
    @Test
    fun wildcardStructureComparisonMatchesExactAndReferencedPaths() = runTest {
        val store = InMemoryDataStore.open(dataModelsById = mapOf(1u to ComplexModel, 2u to StructuralFilterSource), keepAllVersions = true)
        try { assertStructuralFilterEquivalence(store) } finally { store.close() }
    }

    @Test
    fun exactAndFuzzyFiltersConsultTheParentTypeAtTheRequestedVersion() {
        fun retained(values: Values<ComplexModel>): DataRecord<ComplexModel> {
            val rows = mutableListOf<DataRecordValue<Any>>()
            values.writeToStorage { _, qualifier, _, value -> rows += DataRecordValue(qualifier, value, HLC(1uL)) }
            val root = ComplexModel.ref { multi }.toStorageByteArray()
            val oldRoot = rows.first { it.reference.contentEquals(root) }
            val rootHistory = DataRecordHistoricValues(root, listOf(oldRoot, DataRecordValue(root, T1("new"), HLC(2uL))))
            val retainedRows = (rows.filterNot { it.reference.contentEquals(root) } + rootHistory)
                .sortedWith { first, second -> first.reference.compareTo(second.reference) }
            return DataRecord(Key(ByteArray(ComplexModel.Meta.keyByteSize)), retainedRows, HLC(1uL), HLC(2uL))
        }
        fun check(record: DataRecord<ComplexModel>, reference: AnyPropertyReference) {
            @Suppress("UNCHECKED_CAST")
            val typedReference = reference as IsPropertyReference<Any, IsPropertyDefinition<Any>, Any>
            val fetcher = { _: IsRootDataModel, _: Key<*> -> null }
            assertFalse(record.matchQualifier(ComplexModel, typedReference, null, fetcher) { it != null }, "inactive ${reference.completeName}")
            assertTrue(record.matchQualifier(ComplexModel, typedReference, HLC(1uL), fetcher) { it != null }, "historic active ${reference.completeName}")
        }
        val embedded = retained(ComplexModel.create { multi with T3 { value with "old" } })
        check(embedded, ComplexModel.ref { multi atType T3 })
        check(embedded, ComplexModel.ref { multi.atType(T3) { value } })
        val list = retained(ComplexModel.create { multi with T4(listOf("old")) })
        check(list, ComplexModel.ref { multi.atType(T4) { ::anyItemRef } })
    }
}
