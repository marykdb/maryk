package maryk.core.properties.definitions

import kotlinx.datetime.LocalDateTime
import maryk.core.extensions.bytes.writeVarBytes
import maryk.core.models.DataModel
import maryk.core.models.migration.readLegacyTransportStorage
import maryk.core.properties.definitions.contextual.ContextualNumberDefinition
import maryk.core.properties.types.Bytes
import maryk.core.properties.types.TimePrecision
import maryk.core.properties.types.numeric.NumberDescriptor
import maryk.core.properties.types.numeric.NumberType
import maryk.core.properties.types.numeric.SInt8
import maryk.core.properties.types.numeric.SInt16
import maryk.core.properties.types.numeric.SInt64
import maryk.core.properties.types.numeric.UInt8
import maryk.core.properties.types.numeric.UInt16
import maryk.core.properties.types.numeric.UInt32
import maryk.core.properties.types.numeric.UInt64
import maryk.core.protobuf.ProtoBuf
import maryk.core.protobuf.WireType.BIT_32
import maryk.core.protobuf.WireType.BIT_64
import maryk.core.protobuf.WireType.LENGTH_DELIMITED
import maryk.core.protobuf.WireType.VAR_INT
import maryk.core.protobuf.WriteCache
import maryk.core.query.DefinitionsContext
import maryk.core.query.RequestContext
import kotlin.test.assertContentEquals
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.contextual.ContextCaptureDefinition
import maryk.core.properties.definitions.contextual.ContextTransformerDefinition
import maryk.core.properties.definitions.contextual.ContextValueTransformDefinition
import maryk.core.properties.definitions.contextual.ContextualPropertyValueDefinition
import maryk.core.properties.definitions.contextual.ContextualSubDefinition
import maryk.core.properties.definitions.contextual.ContextualValueDefinition
import maryk.core.properties.types.numeric.Float32
import maryk.core.properties.types.numeric.Float64
import maryk.core.properties.types.numeric.SInt32
import maryk.json.JsonReader
import maryk.json.JsonWriter
import maryk.lib.exceptions.ParseException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

private class WireOuterContext : IsPropertyContext
private class WireInnerContext : IsPropertyContext

private object WrappedWireRecord : DataModel<WrappedWireRecord>() {
    val values by list(1u, valueDefinition = ContextValueTransformDefinition(NumberDefinition(type = Float64)) { _: IsPropertyContext?, value -> value })
    val tail by string(2u)
}

class ContextualCollectionWireIntegrityTest {
    @Test
    fun contextTransformingPackedNumbersRoundTrip() {
        val integers = ContextTransformerDefinition<Int, IsPropertyContext, IsPropertyContext>(NumberDefinition(type = SInt32)) { it }
        roundTrip(ListDefinition(valueDefinition = integers), listOf(-1, 0, Int.MAX_VALUE))
    }

    @Test
    fun capturedPackedFloatsRoundTrip() {
        val captures = mutableListOf<Double>()
        val doubles = ContextCaptureDefinition(NumberDefinition(type = Float64)) { _: IsPropertyContext?, value -> captures += value }
        roundTrip(ListDefinition(valueDefinition = doubles), listOf(-0.0, Double.MIN_VALUE, Double.POSITIVE_INFINITY))
        assertEquals(3, captures.size)
    }

    @Test
    fun transformedFixedWidthValuesDoNotRequireAStorageCodecOnTheirWrapper() {
        val floats = ContextValueTransformDefinition(NumberDefinition(type = Float32)) { _: IsPropertyContext?, value -> value }
        val list = ListDefinition(valueDefinition = floats)
        val scalar = NumberDefinition(type = Float32)
        val payload = scalar.toTransportByteArray(1.0f) + scalar.toTransportByteArray(-0.0f)
        var offset = 0
        assertEquals(listOf(1.0f, -0.0f), list.readTransportBytes(payload.size, { payload[offset++] }))
        assertFailsWith<ParseException> { list.readTransportBytes(5, { 0 }) }
    }

    @Test
    fun jsonFormattingUsesTheResolverOuterContext() {
        val outer = WireOuterContext()
        val inner = WireInnerContext()
        val scalar = ContextualValueDefinition(
            contextualResolver = { context: WireOuterContext? -> assertSame(outer, context); StringDefinition() },
            contextTransformer = { _: WireOuterContext? -> inner },
        )
        val sub = ContextualSubDefinition(
            contextualResolver = { context: WireOuterContext? -> assertSame(outer, context); StringDefinition() },
            contextTransformer = { _: WireOuterContext? -> inner },
        )
        for (definition in listOf(ListDefinition(valueDefinition = scalar), SubListDefinition(sub))) {
            val output = StringBuilder()
            definition.writeJsonValue(listOf("one", "two"), JsonWriter(writer = output::append), outer)
            assertEquals("[\"one\",\"two\"]", output.toString())
        }
        val nullable = ContextualValueDefinition(contextualResolver = { _: IsPropertyContext? -> StringDefinition() })
        val output = StringBuilder()
        ListDefinition(valueDefinition = nullable).writeJsonValue(listOf("nullable"), JsonWriter(writer = output::append))
        assertEquals("[\"nullable\"]", output.toString())
    }

    @Test
    fun incompleteCollectionJsonCannotReturnAPartialValue() {
        val list = ListDefinition(valueDefinition = StringDefinition())
        for (input in listOf("[", "[\"one\"", "[\"one\",")) {
            val reader = JsonReader(input)
            reader.nextToken()
            assertFails(input) { list.readJson(reader) }
        }
    }

    @Test
    @Suppress("UNCHECKED_CAST")
    fun everyNumericDescriptorSupportsPackedAndUnpackedReadsThroughScalarWrappers() {
        val cases = listOf(
            SInt8 to listOf(Byte.MIN_VALUE, 0.toByte(), Byte.MAX_VALUE),
            SInt16 to listOf(Short.MIN_VALUE, 0.toShort(), Short.MAX_VALUE),
            SInt32 to listOf(Int.MIN_VALUE, 0, Int.MAX_VALUE),
            SInt64 to listOf(Long.MIN_VALUE, 0L, Long.MAX_VALUE),
            UInt8 to listOf(0.toUByte(), 1.toUByte(), UByte.MAX_VALUE),
            UInt16 to listOf(0.toUShort(), 1.toUShort(), UShort.MAX_VALUE),
            UInt32 to listOf(0u, 1u, UInt.MAX_VALUE),
            UInt64 to listOf(0uL, 1uL, ULong.MAX_VALUE),
            Float32 to listOf(-0.0f, Float.MIN_VALUE, Float.POSITIVE_INFINITY, Float.fromBits(0x7fc00001)),
            Float64 to listOf(-0.0, Double.MIN_VALUE, Double.POSITIVE_INFINITY, Double.fromBits(0x7ff8000000000001L)),
        )
        assertEquals(NumberType.cases().map { it.descriptor() }.toSet(), cases.map { it.first }.toSet())
        for ((descriptor, samples) in cases) {
            val scalar = NumberDefinition(type = descriptor as NumberDescriptor<Comparable<Any>>)
            val variants = listOf<IsSubDefinition<Comparable<Any>, IsPropertyContext>>(
                scalar,
                ContextTransformerDefinition<Comparable<Any>, IsPropertyContext, IsPropertyContext>(scalar) { it },
                ContextCaptureDefinition(scalar) { _: IsPropertyContext?, _ -> },
                ContextValueTransformDefinition(scalar) { _: IsPropertyContext?, value -> value },
                ContextualValueDefinition(contextualResolver = { _: IsPropertyContext? -> scalar }),
                ContextualSubDefinition(contextualResolver = { _: IsPropertyContext? -> scalar }),
                ContextualPropertyValueDefinition(contextualResolver = { _: IsPropertyContext? -> scalar }),
                ContextualNumberDefinition<IsPropertyContext> { descriptor },
            )
            for (element in variants) {
                val list = SubListDefinition(element)
                for (values in listOf(emptyList(), samples as List<Comparable<Any>>)) {
                    val payload = values.fold(byteArrayOf()) { all, value -> all + scalar.toTransportByteArray(value) }
                    val packed = field(payload)
                    val unpacked = buildList {
                        for (value in values) {
                            ProtoBuf.writeKey(1u, scalar.wireType, ::add)
                            scalar.toTransportByteArray(value).forEach(::add)
                        }
                    }.toByteArray()
                    val codec = ContextualPropertyValueDefinition(contextualResolver = { _: IsPropertyContext? -> list })
                    val expectedWriter = if ((element as? IsValueDefinition<*, *>)?.wireType in listOf(VAR_INT, BIT_32, BIT_64)) packed else unpacked
                    assertContentEquals(expectedWriter, codec.toTransportByteArray(values), "${descriptor.type}: writer bytes must remain stable")
                    for (input in listOf(packed, unpacked)) {
                        var offset = 0
                        val decoded = codec.readTransportBytes(input.size, { input[offset++] })
                        assertEquals(values.size, decoded.size, descriptor.type.toString())
                        values.zip(decoded).forEach { (expected, actual) ->
                            assertContentEquals(scalar.toTransportByteArray(expected), scalar.toTransportByteArray(actual), descriptor.type.toString())
                        }
                        assertEquals(input.size, offset)
                        if (element is IsValueDefinition<*, *>) {
                            val set = SetDefinition(valueDefinition = element as IsValueDefinition<Comparable<Any>, IsPropertyContext>)
                            val setCodec = ContextualPropertyValueDefinition(contextualResolver = { _: IsPropertyContext? -> set })
                            offset = 0
                            val decodedSet = setCodec.readTransportBytes(input.size, { input[offset++] })
                            val expectedIdentities = values.map { Bytes(scalar.toTransportByteArray(it)) }.toSet()
                            val actualIdentities = decodedSet.map { Bytes(scalar.toTransportByteArray(it)) }.toSet()
                            assertEquals(expectedIdentities, actualIdentities, descriptor.type.toString())
                            assertEquals(expectedIdentities.size, decodedSet.size)
                            assertEquals(input.size, offset)
                        }
                    }
                }
            }
        }
    }

    @Test
    fun fixedWidthWrappersCheckPackedWidthsBeforeReadingAndRetainFollowingFields() {
        for (width in listOf(4, 8)) {
            val scalar: IsValueDefinition<out Any, IsPropertyContext> = if (width == 4) NumberDefinition(type = Float32) else NumberDefinition(type = Float64)
            @Suppress("UNCHECKED_CAST")
            val wrapped = ContextTransformerDefinition<Any, IsPropertyContext, IsPropertyContext>(scalar as IsValueDefinition<Any, IsPropertyContext>) { it }
            val list = ListDefinition(valueDefinition = wrapped)
            for (length in 1 until width * 3) if (length % width != 0) {
                var reads = 0
                assertFailsWith<ParseException> { list.readTransportBytes(length, { reads++; 0 }) }
                assertEquals(0, reads)
            }
        }
        val original = WrappedWireRecord.create {
            values with listOf(-0.0, Double.fromBits(0x7ff8000000000001L))
            tail with "retained"
        }
        val cache = WriteCache()
        val bytes = ByteArray(WrappedWireRecord.Serializer.calculateProtoBufLength(original, cache))
        var offset = 0
        WrappedWireRecord.Serializer.writeProtoBuf(original, cache, { bytes[offset++] = it })
        offset = 0
        val decoded = WrappedWireRecord.Serializer.readProtoBuf(bytes.size, { bytes[offset++] })
        assertEquals("retained", decoded.get { tail })
        assertEquals((-0.0).toRawBits(), decoded.get { values }!![0].toRawBits())
        assertEquals(0x7ff8000000000001L, decoded.get { values }!![1].toRawBits())
    }

    @Test
    fun collectionValuedElementsKeepTheirEnvelopesAndLegacyNanosKeepTheirPairs() {
        val inner = ListDefinition(valueDefinition = NumberDefinition(type = SInt32))
        val complete = ContextualPropertyValueDefinition(contextualResolver = { _: IsPropertyContext? -> inner })
        val transformed = ContextTransformerDefinition<List<Int>, IsPropertyContext, IsPropertyContext>(complete) { it }
        val outer = ListDefinition(valueDefinition = transformed)
        for (items in listOf(emptyList(), listOf(emptyList()), listOf(listOf(1, 2), emptyList(), listOf(3)))) roundTrip(outer, items)

        val context = RequestContext(DefinitionsContext())
        val nanos = DateTimeDefinition(precision = TimePrecision.NANOS)
        val wrapped = ContextTransformerDefinition<LocalDateTime, IsPropertyContext, IsPropertyContext>(nanos) { context }
        val list = ListDefinition(valueDefinition = wrapped)
        val codec = ContextualPropertyValueDefinition(contextualResolver = { _: IsPropertyContext? -> list })
        val legacy = field(byteArrayOf(8, 1, 16, 42))
        var offset = 0
        val decoded = context.readLegacyTransportStorage { codec.readTransportBytes(legacy.size, { legacy[offset++] }) }
        assertEquals(listOf(LocalDateTime(1970, 1, 1, 0, 0, 8, 1), LocalDateTime(1970, 1, 1, 0, 0, 16, 42)), decoded)
    }

    @Test
    fun completeAndNestedJsonCollectionsRetainTheirShape() {
        val inner = ListDefinition(valueDefinition = StringDefinition())
        val complete = ContextualPropertyValueDefinition(contextualResolver = { _: IsPropertyContext? -> inner })
        val outer = ListDefinition(valueDefinition = complete)
        for (input in listOf("[]", "[[]]", """[["one"],[],["two"]]""")) {
            val reader = JsonReader(input)
            reader.nextToken()
            val decoded = outer.readJson(reader)
            val output = StringBuilder()
            outer.writeJsonValue(decoded, JsonWriter(writer = output::append))
            assertEquals(input, output.toString())
        }
        for (input in listOf("[[", """[["one"]""", """[["one"],""")) {
            val reader = JsonReader(input)
            reader.nextToken()
            assertFails(input) { outer.readJson(reader) }
        }
    }

    @Test
    fun packedReadsApplyValueTransformationsAndCapturesExactlyOncePerElement() {
        val captured = mutableListOf<Int>()
        val transformed = ContextValueTransformDefinition(NumberDefinition(type = SInt32)) { _: IsPropertyContext?, value -> value + 10 }
        val element = ContextCaptureDefinition(transformed) { _: IsPropertyContext?, value -> captured += value }
        val list = ListDefinition(valueDefinition = element)
        val codec = ContextualPropertyValueDefinition(contextualResolver = { _: IsPropertyContext? -> list })
        val bytes = codec.toTransportByteArray(listOf(1, 2, 3))
        var offset = 0
        assertEquals(listOf(11, 12, 13), codec.readTransportBytes(bytes.size, { bytes[offset++] }))
        assertEquals(listOf(11, 12, 13), captured)
    }

    private fun field(payload: ByteArray): ByteArray = buildList {
        ProtoBuf.writeKey(1u, LENGTH_DELIMITED, ::add)
        payload.size.writeVarBytes(::add)
        payload.forEach(::add)
    }.toByteArray()

    private fun <T : Any> roundTrip(definition: IsSubDefinition<T, IsPropertyContext>, value: T) {
        val codec = ContextualPropertyValueDefinition(contextualResolver = { _: IsPropertyContext? -> definition })
        val bytes = codec.toTransportByteArray(value)
        var offset = 0
        assertEquals(value, codec.readTransportBytes(bytes.size, { bytes[offset++] }))
        assertEquals(bytes.size, offset)
    }
}
