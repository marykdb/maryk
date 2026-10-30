package maryk.datastore.indexeddb

import kotlinx.coroutines.test.runTest
import maryk.core.properties.enum.invoke
import maryk.core.properties.references.dsl.atType
import maryk.core.properties.references.AnyPropertyReference
import maryk.core.properties.types.invoke
import maryk.core.query.changes.Change
import maryk.core.query.changes.change
import maryk.core.query.pairs.with
import maryk.core.query.requests.add
import maryk.core.query.requests.change
import maryk.core.query.responses.statuses.AddSuccess
import maryk.core.query.responses.statuses.ChangeSuccess
import maryk.core.values.Values
import maryk.datastore.indexeddb.processors.matchQualifierOnRows
import maryk.datastore.indexeddb.processors.readStorageRows
import maryk.datastore.test.StructuralFilterSource
import maryk.datastore.test.assertStructuralFilterEquivalence
import maryk.test.models.ComplexModel
import maryk.test.models.MarykTypeEnum.T1
import maryk.test.models.MarykTypeEnum.T3
import maryk.test.models.MarykTypeEnum.T4
import maryk.lib.extensions.compare.matchesRangePart
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class StructuralFilterTest {
    @Test
    fun wildcardStructureComparisonMatchesExactAndReferencedPaths() = runTest {
        installIndexedDbForTests()
        val store = IndexedDbDataStore.open(databaseName = "structural-filter-${Random.nextInt()}",
            dataModelsById = mapOf(1u to ComplexModel, 2u to StructuralFilterSource), keepAllVersions = true)
        try { assertStructuralFilterEquivalence(store) } finally { store.close() }
    }
    @Test
    fun referencedRowMatchingIgnoresRetainedInactiveTypedChildren() = runTest {
        installIndexedDbForTests()
        val store = IndexedDbDataStore.open(databaseName = "retained-typed-filter-${Random.nextInt()}",
            dataModelsById = mapOf(1u to ComplexModel), keepAllVersions = true)
        try {
            val cases: List<Pair<Values<ComplexModel>, AnyPropertyReference>> = listOf(
                ComplexModel.create { multi with T3 { value with "old" } } to ComplexModel.ref { multi.atType(T3) { value } },
                ComplexModel.create { multi with T4(listOf("old")) } to ComplexModel.ref { multi.atType(T4) { ::anyItemRef } },
            )
            for ((values, reference) in cases) {
                val added = assertIs<AddSuccess<ComplexModel>>(store.execute(ComplexModel.add(values)).statuses.single())
                val oldRows = assertNotNull(store.readStorageRows(ComplexModel, added.key.bytes, null))
                assertIs<ChangeSuccess<ComplexModel>>(store.execute(ComplexModel.change(added.key.change(
                    Change(ComplexModel.ref { multi } with T1("new")),
                ))).statuses.single())
                val root = ComplexModel.ref { multi }.toStorageByteArray()
                val current = assertNotNull(store.readStorageRows(ComplexModel, added.key.bytes, null))
                val retained = current + oldRows.filter { it.first.size > root.size && it.first.matchesRangePart(0, root) }
                assertTrue(store.matchQualifierOnRows(1u, added.key.bytes, oldRows, reference.toQualifierMatcher()) { it == "old" })
                assertFalse(store.matchQualifierOnRows(1u, added.key.bytes, retained, reference.toQualifierMatcher()) { it != null })
            }
        } finally { store.close() }
    }

}
