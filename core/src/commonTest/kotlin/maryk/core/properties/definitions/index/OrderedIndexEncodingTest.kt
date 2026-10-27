package maryk.core.properties.definitions.index

import maryk.core.models.RootDataModel
import maryk.core.models.migration.convertLegacyIndexBytes
import maryk.core.properties.definitions.wrapper.FixedBytesDefinitionWrapper
import maryk.core.properties.definitions.NumberDefinition
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.definitions.map
import maryk.core.properties.definitions.set
import maryk.core.properties.references.ValueWithFixedBytesPropertyReference
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.references.AnyPropertyReference
import maryk.core.values.IsStreamingValuesGetter
import maryk.core.properties.definitions.number
import maryk.core.properties.definitions.string
import maryk.core.properties.types.numeric.Float32
import maryk.core.properties.types.numeric.Float64
import maryk.core.properties.types.numeric.UInt32
import maryk.lib.extensions.compare.compareTo
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

private object OrderedIndexModel : RootDataModel<OrderedIndexModel>() {
    val double by number(1u, type = Float64)
    val float by number(2u, type = Float32)
    val first by string(3u)
    val second by string(4u)
    val uint by number(5u, type = UInt32)
    val reversedDouble by number(6u, type = Float64, reversedStorage = true)
    val reversedFloat by number(7u, type = Float32, reversedStorage = true)
    val keys by map(8u, keyDefinition = NumberDefinition(type = Float32), valueDefinition = StringDefinition())
    val items by set(9u, valueDefinition = NumberDefinition(type = Float64))
}

class OrderedIndexEncodingTest {
    @Test
    fun ordinaryAndIndexFloatingPointBytesShareNumericOrdering() {
        assertEquals("bff0000000000000", OrderedIndexModel.double.definition.toStorageBytes(1.0).toHexString())
        assertEquals("bf800000", OrderedIndexModel.float.definition.toStorageBytes(1f).toHexString())
        val numbers = listOf(Double.NEGATIVE_INFINITY, -2.0, -1.0, -0.0, 0.0, 1.0, 2.0, Double.POSITIVE_INFINITY, Double.NaN)
        val rows = numbers.map { number ->
            number to OrderedIndexModel.double.ref().toStorageByteArrayForIndex(OrderedIndexModel.create { double with number })!!
        }
        assertEquals(numbers, rows.reversed().sortedWith { a, b -> a.second compareTo b.second }.map { it.first })
        val floatRows = numbers.map { number ->
            number.toFloat() to OrderedIndexModel.float.ref().toStorageByteArrayForIndex(OrderedIndexModel.create { float with number.toFloat() })!!
        }
        assertEquals(numbers.map { it.toFloat() }, floatRows.reversed().sortedWith { a, b -> a.second compareTo b.second }.map { it.first })
    }

    @Test
    fun scalarAndCompoundWritersDoNotUseTheOrdinaryFloatingPointCodec() {
        val reference = object : ValueWithFixedBytesPropertyReference<
            Double, Double, FixedBytesDefinitionWrapper<Double, Double, *, *, *>, AnyPropertyReference
        >(OrderedIndexModel.double, null) {
            override fun writeStorageBytes(value: Double, writer: (Byte) -> Unit): Unit = error("Ordinary write codec used")
            override fun readStorageBytes(length: Int, reader: () -> Byte): Double = error("Ordinary read codec used")
        }
        val values = OrderedIndexModel.create { double with 1.0 }
        val expected = "bff000000000000008".hexToByteArray()
        assertContentEquals(expected, reference.toStorageByteArrayForIndex(values))
        assertEquals(expected.size, reference.calculateStorageByteLengthForIndex(values))
        val written = buildList { reference.writeStorageBytesForIndex(values, null, ::add) }.toByteArray()
        assertContentEquals(expected, written)
        val composite = Multiple(reference)
        assertContentEquals(expected, composite.toStorageByteArrayForIndex(values))
        assertContentEquals("400fffffffffffff08".hexToByteArray(), Reversed(reference).toStorageByteArrayForIndex(values))
        assertContentEquals(expected, reference.toStorageByteArrayForIndexValue(1.0, byteArrayOf()))
        val compoundWritten = buildList { composite.forEachStorageByteArrayForIndex(values, null) { add(it.toHexString()) } }
        assertEquals(listOf(expected.toHexString()), compoundWritten)
    }

    @Test
    fun definitionAndReferenceReversalRetainTheSameBytes() {
        val values = OrderedIndexModel.create { reversedDouble with 1.0; reversedFloat with 1f }
        assertContentEquals("400fffffffffffff08".hexToByteArray(), OrderedIndexModel.reversedDouble.ref().toStorageByteArrayForIndex(values))
        assertContentEquals("bff000000000000008".hexToByteArray(), Reversed(OrderedIndexModel.reversedDouble.ref()).toStorageByteArrayForIndex(values))
        assertContentEquals("407fffff04".hexToByteArray(), OrderedIndexModel.reversedFloat.ref().toStorageByteArrayForIndex(values))
        assertContentEquals("bf80000004".hexToByteArray(), Reversed(OrderedIndexModel.reversedFloat.ref()).toStorageByteArrayForIndex(values))
    }

    @Test
    fun collectionAndCompoundIndexesEncodeStreamedFloatingPointValues() {
        val keys = OrderedIndexModel.ref { keys.anyKey() }
        val items = OrderedIndexModel.ref { items.any() }
        val values = OrderedIndexModel.create { this.keys with mapOf(-2f to "a", 1f to "b"); this.items with setOf(-2.0, 1.0) }
        val streaming = object : IsStreamingValuesGetter {
            override fun <T : Any, D : IsPropertyDefinition<T>, C : Any> get(propertyReference: IsPropertyReference<T, D, C>): T? =
                error("Collection should be streamed")
            override fun streamMapKeys(parent: AnyPropertyReference, emit: (Any) -> Unit): Boolean {
                if (parent != OrderedIndexModel.keys.ref()) return false
                listOf(-2f, 1f).forEach(emit)
                return true
            }
            override fun streamSetValues(parent: AnyPropertyReference, emit: (Any) -> Unit): Boolean {
                if (parent != OrderedIndexModel.items.ref()) return false
                listOf(-2.0, 1.0).forEach(emit)
                return true
            }
        }
        for (index in listOf(keys, items, Multiple(keys, items))) {
            val expected = index.toStorageByteArraysForIndex(values).map { it.toHexString() }
            val actual = buildList { index.forEachStorageByteArrayForIndex(streaming, null) { add(it.toHexString()) } }
            assertEquals(expected, actual)
            assertEquals(actual.first().length / 2, index.calculateStorageByteLengthForIndex(values))
        }
        assertEquals(listOf("3fffffff04", "bf80000004"), keys.toStorageByteArraysForIndex(values).map { it.toHexString() })
        assertEquals(listOf("3fffffffffffffff08", "bff000000000000008"), items.toStorageByteArraysForIndex(values).map { it.toHexString() })
    }

    @Test
    fun variableComponentsOrderPrefixesNullCharactersAndUtf16Consistently() {
        val strings = listOf("", "a", "aa", "az", "b", "b\u0000", "😀", "！")
        val reference = OrderedIndexModel.first.ref()
        val ascending = strings.map { text -> text to reference.toStorageByteArrayForIndex(OrderedIndexModel.create { first with text })!! }
        assertEquals(strings.sorted(), ascending.reversed().sortedWith { a, b -> a.second compareTo b.second }.map { it.first })
        val reversed = Reversed(reference)
        val descending = strings.map { text -> text to reversed.toStorageByteArrayForIndex(OrderedIndexModel.create { first with text })!! }
        assertEquals(strings.sortedDescending(), descending.sortedWith { a, b -> a.second compareTo b.second }.map { it.first })
        val composite = Multiple(reference, OrderedIndexModel.second.ref())
        val short = composite.toStorageByteArrayForIndex(OrderedIndexModel.create { first with "a"; second with "zz" })!!
        val long = composite.toStorageByteArrayForIndex(OrderedIndexModel.create { first with "az"; second with "a" })!!
        assertEquals(true, short < long)
    }

    @Test
    fun legacyEntryConversionPreservesKeysAndFixedWidthIndexes() {
        val key = byteArrayOf(10, 11)
        val values = OrderedIndexModel.create { double with -1.0; uint with 7u; first with "az" }
        val legacyDouble = "c010000000000000".hexToByteArray() + byteArrayOf(8) + key
        assertContentEquals(OrderedIndexModel.double.ref().toStorageByteArrayForIndex(values, key), OrderedIndexModel.double.ref().convertLegacyIndexBytes(legacyDouble, key.size))
        val legacyString = "az".encodeToByteArray() + byteArrayOf(2) + key
        assertContentEquals(OrderedIndexModel.first.ref().toStorageByteArrayForIndex(values, key), OrderedIndexModel.first.ref().convertLegacyIndexBytes(legacyString, key.size))
        val fixed = byteArrayOf(0, 0, 0, 7, 4) + key
        assertContentEquals(fixed, OrderedIndexModel.uint.ref().toStorageByteArrayForIndex(values, key))
        assertContentEquals(fixed, OrderedIndexModel.uint.ref().convertLegacyIndexBytes(fixed, key.size))
    }
}
