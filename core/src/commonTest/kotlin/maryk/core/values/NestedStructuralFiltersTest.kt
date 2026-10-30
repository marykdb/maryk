package maryk.core.values

import maryk.core.models.RootDataModel
import maryk.core.exceptions.RequestException
import maryk.core.processors.datastore.matchers.IsIndexPartialToMatch
import maryk.core.processors.datastore.matchers.UniqueToMatch
import maryk.core.processors.datastore.matchers.convertFilterToIndexPartsToMatch
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.IsChangeableValueDefinition
import maryk.core.properties.definitions.ListDefinition
import maryk.core.properties.definitions.MapDefinition
import maryk.core.properties.definitions.MultiTypeDefinition
import maryk.core.properties.definitions.SetDefinition
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.definitions.list
import maryk.core.properties.definitions.map
import maryk.core.properties.definitions.multiType
import maryk.core.properties.definitions.string
import maryk.core.properties.references.IsPropertyReference
import maryk.core.query.filters.And
import maryk.core.query.filters.Equals
import maryk.core.query.filters.Exists
import maryk.core.query.filters.Not
import maryk.core.query.filters.Or
import maryk.core.query.filters.ValueIn
import maryk.core.query.pairs.ReferenceValuePair
import maryk.core.query.pairs.ReferenceValueSetPair
import maryk.core.query.pairs.with
import maryk.core.properties.types.invoke
import maryk.test.models.MarykTypeEnum
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

private object NestedStructuralFilterRecord : RootDataModel<NestedStructuralFilterRecord>() {
    val scalar by string(1u, unique = true, required = false)
    val lists by map(2u, keyDefinition = StringDefinition(), valueDefinition = ListDefinition(valueDefinition = StringDefinition()))
    val sets by map(3u, keyDefinition = StringDefinition(), valueDefinition = SetDefinition(valueDefinition = StringDefinition()))
    val maps by map(4u, keyDefinition = StringDefinition(), valueDefinition = MapDefinition(keyDefinition = StringDefinition(), valueDefinition = StringDefinition()))
    val typed by multiType(5u, typeEnum = MarykTypeEnum)
    val typedList by list(6u, valueDefinition = MultiTypeDefinition(typeEnum = MarykTypeEnum))
}

class NestedStructuralFiltersTest {
    private val values = NestedStructuralFilterRecord.create(setDefaults = false) {
        lists with mapOf("empty" to emptyList(), "full" to listOf("needle", "extra"))
        sets with mapOf("empty" to emptySet(), "full" to setOf("needle", "extra"))
        maps with mapOf("empty" to emptyMap(), "full" to mapOf("key" to "value"))
        typed with MarykTypeEnum.T4(emptyList())
        typedList with listOf(MarykTypeEnum.T4(emptyList()), MarykTypeEnum.T4(listOf("needle")))
    }

    @Test
    fun exactNestedEmptyStructuresRemainPresentAndComparable() {
        for ((name, expected) in listOf(
            "lists.@empty" to emptyList<String>(), "sets.@empty" to emptySet<String>(), "maps.@empty" to emptyMap<String, String>(),
            "typed.*T4" to emptyList<String>(), "typedList.@0.*T4" to emptyList<String>(),
        )) {
            val pair = pair(name, expected)
            assertTrue(values.matches(Exists(pair.reference)), name)
            assertTrue(values.matches(Equals(pair)), name)
        }
        assertTrue(values.matches(And(
            Exists(pair("lists.@empty", emptyList<String>()).reference),
            Not(Exists(pair("lists.@missing", emptyList<String>()).reference)),
            Or(Exists(pair("sets.@empty", emptySet<String>()).reference), Exists(pair("maps.@missing", emptyMap<String, String>()).reference)),
        )))
        assertFalse(values.matches(Exists(pair("lists.@missing", emptyList<String>()).reference)))
    }

    @Test
    fun exactCollectionComparisonsKeepSubsetSemanticsAndWildcardsMatchMembers() {
        assertTrue(values.matches(Equals(pair("lists.@full", listOf("needle")))))
        assertTrue(values.matches(Equals(pair("lists.@full", emptyList<String>()))))
        assertTrue(values.matches(Equals(pair("sets.@full", setOf("needle")))))
        assertTrue(values.matches(Equals(pair("sets.@full", emptySet<String>()))))
        assertFalse(values.matches(Equals(pair("maps.@full", emptyMap<String, String>()))))
        assertTrue(values.matches(Equals(NestedStructuralFilterRecord.ref { typedList.any() } with MarykTypeEnum.T4(listOf("needle")))))
        assertTrue(values.matches(Exists(NestedStructuralFilterRecord.ref { maps.anyKey() })))
        val empty = NestedStructuralFilterRecord.create(setDefaults = false) {
            lists with emptyMap(); sets with emptyMap(); maps with emptyMap()
            typed with MarykTypeEnum.T4(emptyList()); typedList with emptyList()
        }
        assertFalse(empty.matches(Exists(NestedStructuralFilterRecord.ref { maps.anyKey() })))
        assertFalse(empty.matches(Exists(NestedStructuralFilterRecord.ref { typedList.any() })))
        assertTrue(empty.matches(Exists(NestedStructuralFilterRecord.ref { lists })))
    }

    @Test
    fun indexPlanningLeavesStructuralPredicatesForValueMatching() {
        for ((name, value) in listOf(
            "sets.@empty" to emptySet<String>(), "lists.@empty" to emptyList<String>(), "maps.@empty" to emptyMap<String, String>(),
        )) {
            val structural = pair(name, value)
            val partials = mutableListOf<IsIndexPartialToMatch>()
            val uniques = mutableListOf<UniqueToMatch>()
            val equals = mutableListOf<ReferenceValuePair<Any>>()
            convertFilterToIndexPartsToMatch(NestedStructuralFilterRecord.scalar.ref(), NestedStructuralFilterRecord.Meta.keyByteSize,
                null, Equals(structural), partials, equals, uniques)
            assertTrue(partials.isEmpty(), name)
            assertTrue(uniques.isEmpty(), name)
            assertEquals(listOf(structural), equals)
            convertFilterToIndexPartsToMatch(NestedStructuralFilterRecord.scalar.ref(), NestedStructuralFilterRecord.Meta.keyByteSize,
                null, ValueIn(ReferenceValueSetPair(structural.reference, setOf(structural.value))), partials, listOfUniqueFilters = uniques)
            assertTrue(partials.isEmpty(), name)
            assertTrue(uniques.isEmpty(), name)
        }
        val uniques = mutableListOf<UniqueToMatch>()
        val partials = mutableListOf<IsIndexPartialToMatch>()
        convertFilterToIndexPartsToMatch(NestedStructuralFilterRecord.scalar.ref(), NestedStructuralFilterRecord.Meta.keyByteSize,
            null, Equals(NestedStructuralFilterRecord.scalar.ref() with "indexed"), partials, listOfUniqueFilters = uniques)
        assertEquals(1, partials.size)
        assertEquals(1, uniques.size)
    }

    @Test
    fun collectionValuedMapWildcardsReportTheirExplicitContractLimit() {
        for (createReference in listOf<() -> Any>(
            { NestedStructuralFilterRecord.ref { lists.anyValue() } },
            { NestedStructuralFilterRecord.ref { sets.anyValue() } },
            { NestedStructuralFilterRecord.ref { maps.anyValue() } },
        )) {
            val error = assertFailsWith<RequestException> { createReference() }
            assertTrue(error.message.orEmpty().contains("explicit map key"))
        }
    }

    private fun pair(name: String, value: Any): ReferenceValuePair<Any> {
        @Suppress("UNCHECKED_CAST")
        val reference = NestedStructuralFilterRecord.getPropertyReferenceByName(name) as IsPropertyReference<Any, IsChangeableValueDefinition<Any, IsPropertyContext>, *>
        return ReferenceValuePair(reference, value)
    }
}
