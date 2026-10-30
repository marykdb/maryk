package maryk.core.properties.definitions

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.contextual.DataModelReference
import maryk.core.properties.types.Bytes
import maryk.core.properties.types.Decimal
import maryk.core.properties.types.GeoPoint
import maryk.core.properties.types.Key
import maryk.core.properties.types.TypedValue
import maryk.core.properties.types.TimePrecision
import maryk.core.properties.types.numeric.Float32
import maryk.core.properties.types.numeric.Float64
import maryk.core.properties.types.numeric.SInt8
import maryk.core.properties.types.numeric.SInt16
import maryk.core.properties.types.numeric.SInt32
import maryk.core.properties.types.numeric.SInt64
import maryk.core.properties.types.numeric.UInt8
import maryk.core.properties.types.numeric.UInt16
import maryk.core.properties.types.numeric.UInt32
import maryk.core.properties.types.numeric.UInt64
import maryk.core.protobuf.WriteCache
import maryk.core.query.DefinitionsContext
import maryk.test.models.EmbeddedMarykModel
import maryk.test.models.MarykTypeEnum
import maryk.test.models.Option
import maryk.test.models.TestMarykModel
import maryk.test.models.TestValueObject
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

/** Boundary values and a fixed bit grammar make storage normalization and transport agree. */
class ScalarStorageIntegrityGrammarTest {
    @Test
    fun everyNumericDescriptorRetainsExtremaAndDeterministicBitPatterns() {
        val bits = buildList {
            addAll(listOf(Long.MIN_VALUE, Long.MIN_VALUE + 1, -65536L, -129L, -128L, -1L, 0L, 1L, 127L, 128L, 65535L, Long.MAX_VALUE - 1, Long.MAX_VALUE))
            var state = 0x4d4152594bL
            repeat(64) {
                state = state xor (state shl 13)
                state = state xor (state ushr 7)
                state = state xor (state shl 17)
                add(state)
            }
        }
        for (reversed in listOf(false, true)) {
            verify(NumberDefinition(type = SInt8, reversedStorage = reversed), bits.map { it.toByte() })
            verify(NumberDefinition(type = SInt16, reversedStorage = reversed), bits.map { it.toShort() })
            verify(NumberDefinition(type = SInt32, reversedStorage = reversed), bits.map { it.toInt() })
            verify(NumberDefinition(type = SInt64, reversedStorage = reversed), bits)
            verify(NumberDefinition(type = UInt8, reversedStorage = reversed), bits.map { it.toUByte() })
            verify(NumberDefinition(type = UInt16, reversedStorage = reversed), bits.map { it.toUShort() })
            verify(NumberDefinition(type = UInt32, reversedStorage = reversed), bits.map { it.toUInt() })
            verify(NumberDefinition(type = UInt64, reversedStorage = reversed), bits.map { it.toULong() })
            verify(NumberDefinition(type = Float32, reversedStorage = reversed),
                bits.map { Float.fromBits(it.toInt()) } + listOf(-0.0f, 0.0f, Float.MIN_VALUE, -Float.MIN_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE,
                    Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY, Float.fromBits(0x7fc00001), Float.fromBits(0x7fc00002), Float.fromBits(0xffc00001u.toInt())))
            verify(NumberDefinition(type = Float64, reversedStorage = reversed),
                bits.map(Double::fromBits) + listOf(-0.0, 0.0, Double.MIN_VALUE, -Double.MIN_VALUE, Double.MAX_VALUE, -Double.MAX_VALUE,
                    Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, Double.fromBits(0x7ff8000000000001L), Double.fromBits(0x7ff8000000000002L), Double.fromBits(0xfff8000000000001uL.toLong())))
        }
    }

    @Test
    fun scalarFamiliesRetainEmptyBoundaryAndNormalizedValues() {
        verify(BooleanDefinition(), listOf(false, true))
        verify(StringDefinition(), listOf("", "\u0000", "a.\\@#*", "\uD83D\uDE42", "e\u0301", "é", "x".repeat(127), "x".repeat(128), "x".repeat(256)))
        verify(FlexBytesDefinition(), listOf(0, 1, 127, 128, 255, 256).map { size -> Bytes(ByteArray(size) { (it * 127).toByte() }) })
        for (size in listOf(1, 2, 16, 128)) {
            verify(FixedBytesDefinition(byteSize = size), listOf(Bytes(ByteArray(size)), Bytes(ByteArray(size) { -1 }), Bytes(ByteArray(size) { (it * 127).toByte() })))
        }
        verify(DateDefinition(), listOf(Int.MIN_VALUE, -1, 0, 1, Int.MAX_VALUE).map(LocalDate::fromEpochDays))
        for (precision in TimePrecision.entries) {
            verify(TimeDefinition(precision = precision), listOf(LocalTime(0, 0), LocalTime(0, 0, 0, 1), LocalTime(12, 30, 59, 999999999), LocalTime(23, 59, 59, 999999999)))
            verify(DateTimeDefinition(precision = precision), listOf(LocalDateTime(1, 1, 1, 0, 0), LocalDateTime(1969, 12, 31, 23, 59, 59, 999999999),
                LocalDateTime(1970, 1, 1, 0, 0), LocalDateTime(2000, 2, 29, 12, 30, 59, 1), LocalDateTime(9999, 12, 31, 23, 59, 59, 999999999)))
        }
        verify(GeoPointDefinition(), listOf(GeoPoint(-90.0, -180.0), GeoPoint(-0.0, 0.0), GeoPoint(0.00000001, -0.00000001), GeoPoint(90.0, 180.0)))
        verify(EnumDefinition(enum = Option), listOf(Option.V0, Option.V1, Option.V2, Option.V3))
        verify(ReferenceDefinition(dataModel = { TestMarykModel }), listOf(0, 127, 255).map { byte -> Key<TestMarykModel>(ByteArray(TestMarykModel.Meta.keyByteSize) { byte.toByte() }) })
        verify(ValueObjectDefinition(dataModel = TestValueObject), listOf(TestValueObject(0, LocalDateTime(1970, 1, 1, 0, 0), false), TestValueObject(1, LocalDateTime(2000, 2, 29, 12, 30), true)))
        for (size in listOf(1, 2, 8, 16)) {
            for (scale in listOf(0u, 2u, 18u)) {
                val values = listOf(Decimal.fromSignedBytes(ByteArray(size).also { it[0] = 0x80.toByte() }, scale),
                    Decimal.fromUnscaled(0, scale), Decimal.fromSignedBytes(ByteArray(size) { if (it == 0) 0x7f else -1 }, scale))
                verify(DecimalDefinition(scale = scale, byteSize = size), values)
                verify(DecimalDefinition(scale = scale, byteSize = size, reversedStorage = true), values)
            }
        }
    }

    @Test
    fun everyPersistedDefinitionFamilyHasAStableSchemaTransportFixedPoint() {
        val string = StringDefinition(default = "")
        val samples = listOf<IsTransportablePropertyDefinitionType<*>>(
            BooleanDefinition(default = false), DateDefinition(default = LocalDate(1970, 1, 1)),
            DateTimeDefinition(precision = TimePrecision.NANOS, default = LocalDateTime(1970, 1, 1, 0, 0, 0, 1)),
            EmbeddedValuesDefinition(dataModel = { EmbeddedMarykModel }), EnumDefinition(enum = Option),
            FixedBytesDefinition(byteSize = 1, default = Bytes(byteArrayOf(0))), FlexBytesDefinition(default = Bytes(byteArrayOf())),
            GeoPointDefinition(default = GeoPoint(0.0, 0.0)), IncrementingMapDefinition(keyNumberDescriptor = UInt32, valueDefinition = string),
            ListDefinition(valueDefinition = string, default = emptyList()), MapDefinition(keyDefinition = string, valueDefinition = string, default = emptyMap()),
            MultiTypeDefinition(typeEnum = MarykTypeEnum), NumberDefinition(type = SInt64, default = Long.MIN_VALUE),
            ReferenceDefinition(dataModel = { TestMarykModel }), SetDefinition(valueDefinition = string, default = emptySet()),
            string, TimeDefinition(precision = TimePrecision.NANOS, default = LocalTime(0, 0, 0, 1)),
            ValueObjectDefinition(dataModel = TestValueObject), DecimalDefinition(scale = 18u, byteSize = 16, default = Decimal.parse("0.000000000000000001")),
        )
        assertEquals(PropertyDefinitionType.entries.toSet(), samples.map { it.propertyDefinitionType }.toSet())
        val codec = InternalMultiTypeDefinition(typeEnum = PropertyDefinitionType, definitionMap = mapOfPropertyDefEmbeddedObjectDefinitions)
        fun context() = DefinitionsContext(dataModels = mutableMapOf(
            EmbeddedMarykModel.Meta.name to DataModelReference(EmbeddedMarykModel),
            TestMarykModel.Meta.name to DataModelReference(TestMarykModel),
            TestValueObject.Meta.name to DataModelReference(TestValueObject),
        ))
        fun encode(value: TypedValue<PropertyDefinitionType, IsTransportablePropertyDefinitionType<*>>): ByteArray {
            val cache = WriteCache()
            val context = context()
            val bytes = ByteArray(codec.calculateTransportByteLength(value, cache, context))
            var offset = 0
            codec.writeTransportBytes(value, cache, { bytes[offset++] = it }, context)
            assertEquals(bytes.size, offset)
            return bytes
        }
        for (sample in samples) {
            val bytes = encode(TypedValue(sample.propertyDefinitionType, sample))
            var offset = 0
            val converted = codec.readTransportBytes(bytes.size, { bytes[offset++] }, context())
            assertEquals(bytes.size, offset, sample.propertyDefinitionType.name)
            assertEquals(sample.propertyDefinitionType, converted.type)
            assertContentEquals(bytes, encode(converted), sample.propertyDefinitionType.name)
        }
    }

    private fun <T : Any> verify(definition: IsSimpleValueDefinition<T, IsPropertyContext>, values: List<T>) {
        for (value in values) {
            val message = "${definition.propertyDefinitionTypeName()} $value"
            val storage = definition.toStorageBytes(value)
            assertEquals(definition.calculateStorageByteLength(value), storage.size, message)
            var offset = 0
            val stored = definition.readStorageBytes(storage.size) { storage[offset++] }
            assertEquals(storage.size, offset, message)
            assertContentEquals(storage, definition.toStorageBytes(stored), "$message storage fixed point")

            val cache = WriteCache()
            val transport = ByteArray(definition.calculateTransportByteLength(value, cache))
            offset = 0
            definition.writeTransportBytes(value, cache, { transport[offset++] = it })
            assertEquals(transport.size, offset, message)
            offset = 0
            val transported = definition.readTransportBytes(transport.size, { transport[offset++] })
            assertEquals(transport.size, offset, message)
            assertContentEquals(storage, definition.toStorageBytes(transported), "$message transport/storage identity")
        }
    }

    private fun IsPropertyDefinition<*>.propertyDefinitionTypeName() =
        (this as? IsTransportablePropertyDefinitionType<*>)?.propertyDefinitionType?.name ?: toString()
}
