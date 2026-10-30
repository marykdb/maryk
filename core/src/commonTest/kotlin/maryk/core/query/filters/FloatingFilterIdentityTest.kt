package maryk.core.query.filters

import maryk.core.models.RootDataModel
import maryk.core.properties.definitions.NumberDefinition
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.definitions.list
import maryk.core.properties.definitions.map
import maryk.core.properties.definitions.number
import maryk.core.properties.definitions.set
import maryk.core.properties.types.numeric.Float32
import maryk.core.properties.types.numeric.Float64
import maryk.core.query.pairs.with
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private object FloatingFilterValues : RootDataModel<FloatingFilterValues>() {
    val double by number(1u, type = Float64)
    val float by number(2u, type = Float32)
    val sequence by list(3u, valueDefinition = NumberDefinition(type = Float64))
    val members by set(4u, valueDefinition = NumberDefinition(type = Float64))
    val keyed by map(5u, keyDefinition = NumberDefinition(type = Float64), valueDefinition = StringDefinition())
    val mapped by map(6u, keyDefinition = StringDefinition(), valueDefinition = NumberDefinition(type = Float64))
}

class FloatingFilterIdentityTest {
    @Test
    fun ordinaryFloatingEqualityAndValueInUseTheDeclaredStorageWidth() {
        val doubleA = Double.fromBits(0x7ff8000000000001L)
        val doubleB = Double.fromBits(0x7ff8000000000002L)
        val floatA = Float.fromBits(0x7fc00001)
        val floatB = Float.fromBits(0x7fc00002)
        val values = FloatingFilterValues.create { double with doubleA; float with floatA }
        assertTrue(values.matches(Equals(FloatingFilterValues.ref { double } with doubleA)))
        assertFalse(values.matches(Equals(FloatingFilterValues.ref { double } with doubleB)))
        assertTrue(values.matches(ValueIn(FloatingFilterValues.ref { double } with setOf(doubleA))))
        assertFalse(values.matches(ValueIn(FloatingFilterValues.ref { double } with setOf(doubleB))))
        assertTrue(values.matches(Equals(FloatingFilterValues.ref { float } with floatA)))
        assertFalse(values.matches(Equals(FloatingFilterValues.ref { float } with floatB)))
        assertTrue(values.matches(ValueIn(FloatingFilterValues.ref { float } with setOf(floatA))))
        assertFalse(values.matches(ValueIn(FloatingFilterValues.ref { float } with setOf(floatB))))
        assertTrue(values.matches(Not(Equals(FloatingFilterValues.ref { double } with doubleB))))
        val zero = FloatingFilterValues.create { double with 0.0; float with 0.0f }
        assertFalse(zero.matches(Equals(FloatingFilterValues.ref { double } with -0.0)))
        assertFalse(zero.matches(Equals(FloatingFilterValues.ref { float } with -0.0f)))
    }

    @Test
    fun ordinaryListAndMapValuesKeepRawIdentityInAggregateAndScalarCallbacks() {
        val valueA = Double.fromBits(0x7ff8000000000001L)
        val valueB = Double.fromBits(0x7ff8000000000002L)
        val values = FloatingFilterValues.create { sequence with listOf(valueA); mapped with mapOf("value" to valueA) }
        assertTrue(values.matches(Equals(FloatingFilterValues.ref { sequence } with listOf(valueA))))
        assertFalse(values.matches(Equals(FloatingFilterValues.ref { sequence } with listOf(valueB))))
        assertTrue(values.matches(Equals(FloatingFilterValues.ref { sequence.any() } with valueA)))
        assertFalse(values.matches(Equals(FloatingFilterValues.ref { sequence.any() } with valueB)))
        assertTrue(values.matches(Equals(FloatingFilterValues.ref { mapped at "value" } with valueA)))
        assertFalse(values.matches(Equals(FloatingFilterValues.ref { mapped at "value" } with valueB)))
        assertFalse(values.matches(Equals(FloatingFilterValues.ref { mapped.anyValue() } with valueB)))
        val ref = FloatingFilterValues.ref { mapped.anyValue() }
        assertFalse(matchesFilter(Equals(ref with valueB), { _, matcher -> matcher(valueA) }))
        assertFalse(matchesFilter(Equals(ref with valueB), { _, matcher -> matcher(listOf(valueA)) }))
    }

    @Test
    fun qualifierKeysAndSetItemsKeepCanonicalNanButSignedZeroIdentity() {
        val valueA = Double.fromBits(0x7ff8000000000001L)
        val valueB = Double.fromBits(-2251799813685246L)
        val values = FloatingFilterValues.create { members with setOf(valueA); keyed with mapOf(valueA to "present") }
        assertTrue(values.matches(Equals(FloatingFilterValues.ref { members } with setOf(valueB))))
        assertTrue(values.matches(Equals(FloatingFilterValues.ref { members.any() } with valueB)))
        assertTrue(values.matches(Equals(FloatingFilterValues.ref { keyed.anyKey() } with valueB)))
        val ref = FloatingFilterValues.ref { keyed.anyKey() }
        assertTrue(matchesFilter(Equals(ref with valueB), { _, matcher -> matcher(valueA) }))
        val zero = FloatingFilterValues.create { members with setOf(0.0); keyed with mapOf(0.0 to "present") }
        assertFalse(zero.matches(Equals(FloatingFilterValues.ref { members.any() } with -0.0)))
        assertFalse(zero.matches(Equals(FloatingFilterValues.ref { keyed.anyKey() } with -0.0)))
    }
}
