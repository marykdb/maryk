package maryk.core.models

import maryk.core.models.migration.convertLegacyFloatingPointBytes
import maryk.core.models.migration.convertLegacyStorageQualifier
import maryk.core.models.migration.readLegacyValueStorage
import maryk.core.models.migration.readLegacyTransportStorage
import maryk.core.processors.datastore.writeToStorage
import maryk.core.properties.definitions.ListDefinition
import maryk.core.properties.definitions.MapDefinition
import maryk.core.properties.definitions.NumberDefinition
import maryk.core.properties.definitions.ValueObjectDefinition
import maryk.core.properties.definitions.map
import maryk.core.properties.definitions.embed
import maryk.core.properties.definitions.list
import maryk.core.properties.definitions.number
import maryk.core.properties.definitions.set
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.definitions.contextual.ModelContext
import maryk.core.properties.types.ValueDataObject
import maryk.core.properties.types.numeric.Float32
import maryk.core.properties.types.numeric.Float64
import maryk.core.query.DefinitionsContext
import maryk.core.query.DefinitionsConversionContext
import maryk.core.query.RequestContext
import maryk.core.protobuf.WriteCache
import maryk.core.values.ObjectValues
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

private data class FloatingValue(val double: Double, val float: Float) : ValueDataObject(toBytes(double, float)) {
    companion object : ValueDataModel<FloatingValue, Companion>(FloatingValue::class) {
        val double by number(1u, FloatingValue::double, type = Float64)
        val float by number(2u, FloatingValue::float, type = Float32)
        override fun invoke(values: ObjectValues<FloatingValue, Companion>) = FloatingValue(values(1u), values(2u))
    }
}

private object NestedFloatingModel : RootDataModel<NestedFloatingModel>() {
    val nested by map(1u, keyDefinition = NumberDefinition(type = Float32), valueDefinition = MapDefinition(
        keyDefinition = NumberDefinition(type = Float64), valueDefinition = ListDefinition(valueDefinition = NumberDefinition(type = Float32)),
    ))
}

private object FloatingReferenceModel : RootDataModel<FloatingReferenceModel>() {
    val mapped by map(1u, keyDefinition = ValueObjectDefinition(dataModel = FloatingValue), valueDefinition = StringDefinition())
    val items by set(2u, valueDefinition = ValueObjectDefinition(dataModel = FloatingValue))
}

private object NumericNanQualifierModel : RootDataModel<NumericNanQualifierModel>() {
    val doubles by map(1u, keyDefinition = NumberDefinition(type = Float64), valueDefinition = StringDefinition())
    val floats by set(2u, valueDefinition = NumberDefinition(type = Float32))
}

private object EmbeddedFloatingList : DataModel<EmbeddedFloatingList>() {
    val values by list(1u, valueDefinition = NumberDefinition(type = Float64))
}

private object EmbeddedFloatingQualifierModel : RootDataModel<EmbeddedFloatingQualifierModel>() {
    val detail by embed(7u, dataModel = { EmbeddedFloatingList })
}

class LegacyFloatingPointStorageTest {
    @Test
    fun releasedEmbeddedListPropertyTagDoesNotConsumeTheFirstPositionByte() {
        // Exact qualifier exported from the native 0.13.0 fixture: embedded7,
        // named child list1, then its four-byte position. There is no anonymous tag.
        val qualifier = "3e0a00000000".hexToByteArray()
        val converted = EmbeddedFloatingQualifierModel.convertLegacyStorageQualifier(qualifier)
        assertContentEquals(qualifier, converted.bytes)
        assertEquals(qualifier.size, converted.length)
        assertEquals(NumberDefinition(type = Float64), converted.definition)
        assertEquals(true, converted.valueCell)
    }

    @Test
    fun legacyNanQualifierPayloadsConvergeBeforeCollisionPreflight() {
        val doubleReference = NumericNanQualifierModel.ref { doubles at Double.NaN }
        val floatReference = NumericNanQualifierModel.ref { floats item Float.NaN }
        val doubleQualifier = doubleReference.toStorageByteArray()
        val floatQualifier = floatReference.toStorageByteArray()
        for (bits in listOf(0x7ff8000000000000L, 0x7ff8000000000001L, -0x7ffffffffffffL)) {
            val legacy = doubleQualifier.copyOfRange(0, doubleQualifier.size - 8) + legacyBytes(bits, 8)
            assertContentEquals(doubleQualifier, NumericNanQualifierModel.convertLegacyStorageQualifier(legacy).bytes)
        }
        for (bits in listOf(0x7fc00000, 0x7fc00001, -0x3fffff)) {
            val legacy = floatQualifier.copyOfRange(0, floatQualifier.size - 4) + legacyBytes(bits.toLong(), 4)
            assertContentEquals(floatQualifier, NumericNanQualifierModel.convertLegacyStorageQualifier(legacy).bytes)
        }
    }

    @Test
    fun legacyValueObjectMapAndSetReferencesUseTheMigrationContext() {
        val value = FloatingValue(-1.0, 1f)
        val oldValue = legacyBytes((-1.0).toRawBits(), 8) + byteArrayOf(1) + legacyBytes(1f.toRawBits().toLong(), 4)
        val context = RequestContext(DefinitionsContext())
        for (reference in listOf(FloatingReferenceModel.ref { mapped at value }, FloatingReferenceModel.ref { items item value })) {
            val cache = WriteCache()
            val current = ByteArray(reference.calculateTransportByteLength(cache))
            var offset = 0
            reference.writeTransportBytes(cache) { current[offset++] = it }
            val legacy = replace(current, value._bytes, oldValue)
            offset = 0
            val converted = context.readLegacyTransportStorage {
                FloatingReferenceModel.getPropertyReferenceByBytes(legacy.size, { legacy[offset++] }, context)
            }
            assertEquals(reference, converted)
            assertContentEquals(reference.toStorageByteArray(), converted.toStorageByteArray())
        }
    }

    @Test
    fun convertsBothWidthsReversalSignedZerosInfinitiesAndRawNanPayloads() {
        val doubles = listOf(Double.NEGATIVE_INFINITY, -2.0, -0.0, 0.0, Double.MIN_VALUE, 1.0, Double.POSITIVE_INFINITY,
            Double.fromBits(0x7ff8000000000001L), Double.fromBits(-0x7ffffffffffffL))
        for (reversed in listOf(false, true)) {
            val definition = NumberDefinition(type = Float64, reversedStorage = reversed)
            for (value in doubles) {
                val legacy = legacyBytes(value.toRawBits(), 8, reversed)
                val converted = definition.convertLegacyFloatingPointBytes(legacy)
                assertContentEquals(definition.toStorageBytes(value), converted)
                var offset = 0
                assertEquals(value.toRawBits(), definition.readStorageBytes(converted.size) { converted[offset++] }.toRawBits())
            }
            val floats = listOf(Float.NEGATIVE_INFINITY, -2f, -0f, 0f, Float.MIN_VALUE, 1f, Float.POSITIVE_INFINITY,
                Float.fromBits(0x7fc00001), Float.fromBits(-0x3fffff))
            val singleDefinition = NumberDefinition(type = Float32, reversedStorage = reversed)
            for (value in floats) {
                val converted = singleDefinition.convertLegacyFloatingPointBytes(legacyBytes(value.toRawBits().toLong(), 4, reversed))
                assertContentEquals(singleDefinition.toStorageBytes(value), converted)
                var offset = 0
                assertEquals(value.toRawBits(), singleDefinition.readStorageBytes(converted.size) { converted[offset++] }.toRawBits())
            }
        }
    }

    @Test
    fun convertsNestedMapKeysWithoutChangingListPositionsOrSizeQualifiers() {
        val values = NestedFloatingModel.create { nested with mapOf(1f to mapOf(-2.0 to listOf(1f, -2f))) }
        var convertedKeys = 0
        values.writeToStorage { _, qualifier, _, _ ->
            var legacy = replace(qualifier, NumberDefinition(type = Float32).toStorageBytes(1f), legacyBytes(1f.toRawBits().toLong(), 4))
            legacy = replace(legacy, NumberDefinition(type = Float64).toStorageBytes(-2.0), legacyBytes((-2.0).toRawBits(), 8))
            val converted = NestedFloatingModel.convertLegacyStorageQualifier(legacy)
            assertContentEquals(qualifier, converted.bytes)
            assertEquals(qualifier.size, converted.length)
            if (!legacy.contentEquals(qualifier)) convertedKeys++
        }
        assertEquals(4, convertedKeys)
    }

    @Test
    fun convertsPackedValueObjectsAndReadsOldTransportOnlyInMigrationContext() {
        val definition = ValueObjectDefinition(dataModel = FloatingValue)
        val expected = FloatingValue(-1.0, 1f)
        val legacy = legacyBytes((-1.0).toRawBits(), 8) + byteArrayOf(1) + legacyBytes(1f.toRawBits().toLong(), 4)
        assertContentEquals(expected._bytes, definition.convertLegacyFloatingPointBytes(legacy))
        val context = RequestContext(DefinitionsContext())
        var offset = 0
        val read = context.readLegacyValueStorage { definition.readTransportBytes(legacy.size, { legacy[offset++] }, context, null) }
        assertEquals(expected, read)
        offset = 0
        assertEquals(expected, definition.readTransportBytes(expected._bytes.size, { expected._bytes[offset++] }, context, null))
        val definitions = DefinitionsConversionContext()
        offset = 0
        assertEquals(expected, definitions.readLegacyTransportStorage {
            definition.readTransportBytes(legacy.size, { legacy[offset++] }, ModelContext(definitions), null)
        })
    }

    private fun legacyBytes(bits: Long, size: Int, reversed: Boolean = false): ByteArray {
        val sign = if (size == 4) Int.MIN_VALUE.toLong() else Long.MIN_VALUE
        val transformed = (bits xor ((bits shr (size * 8 - 1)) or sign)) + 1
        return ByteArray(size) { index ->
            var byte = (transformed ushr ((size - 1 - index) * 8)).toByte()
            if (index == 0) byte = (byte.toInt() xor 128).toByte()
            if (reversed) byte.toInt().inv().toByte() else byte
        }
    }

    private fun replace(bytes: ByteArray, from: ByteArray, to: ByteArray): ByteArray = bytes.copyOf().also { result ->
        for (offset in 0..bytes.size - from.size) {
            if (from.indices.all { bytes[offset + it] == from[it] }) to.copyInto(result, offset)
        }
    }
}
