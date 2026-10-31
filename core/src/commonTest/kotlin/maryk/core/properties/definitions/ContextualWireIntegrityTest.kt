package maryk.core.properties.definitions

import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.contextual.ContextCaptureDefinition
import maryk.core.properties.definitions.contextual.ContextTransformerDefinition
import maryk.core.properties.definitions.contextual.ContextValueTransformDefinition
import maryk.core.properties.definitions.contextual.ContextualValueDefinition
import maryk.core.properties.definitions.contextual.ContextualPropertyValueDefinition
import maryk.core.properties.types.numeric.SInt32
import maryk.core.properties.types.numeric.SInt8
import maryk.core.properties.types.numeric.SInt16
import maryk.core.properties.types.numeric.SInt64
import maryk.core.properties.types.numeric.UInt8
import maryk.core.properties.types.numeric.UInt16
import maryk.core.properties.types.numeric.UInt32
import maryk.core.properties.types.numeric.UInt64
import maryk.core.properties.types.numeric.Float32
import maryk.core.properties.types.numeric.Float64
import maryk.core.protobuf.ProtoBuf
import maryk.core.protobuf.WireType.BIT_32
import maryk.core.protobuf.WireType.BIT_64
import maryk.core.protobuf.WireType.LENGTH_DELIMITED
import maryk.core.protobuf.WireType.VAR_INT
import maryk.core.protobuf.WriteCache
import maryk.lib.exceptions.ParseException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith

class ContextualWireIntegrityTest {
    @Test
    fun contextWrappersAcceptTheWireTypeTheyActuallyWrite() {
        verifyWrappers(NumberDefinition(type = SInt8), (-1).toByte())
        verifyWrappers(NumberDefinition(type = SInt16), Short.MIN_VALUE)
        verifyWrappers(NumberDefinition(type = SInt32), 1)
        verifyWrappers(NumberDefinition(type = SInt64), Long.MIN_VALUE)
        verifyWrappers(NumberDefinition(type = UInt8), UByte.MAX_VALUE)
        verifyWrappers(NumberDefinition(type = UInt16), UShort.MAX_VALUE)
        verifyWrappers(NumberDefinition(type = UInt32), UInt.MAX_VALUE)
        verifyWrappers(NumberDefinition(type = UInt64), ULong.MAX_VALUE)
        verifyWrappers(NumberDefinition(type = Float32), -0.0f)
        verifyWrappers(NumberDefinition(type = Float64), Double.fromBits(0x7ff8000000000001L))
        verifyWrappers(StringDefinition(), "value")
    }

    private fun <T : Any> verifyWrappers(resolved: IsValueDefinition<T, IsPropertyContext>, value: T) {
        val contextual = ContextualValueDefinition(contextualResolver = { _: IsPropertyContext? -> resolved })
        val wrappers = listOf<IsValueDefinition<T, IsPropertyContext>>(
            contextual,
            ContextTransformerDefinition<T, IsPropertyContext, IsPropertyContext>(contextual) { it },
            ContextValueTransformDefinition(contextual) { _, value -> value },
            ContextCaptureDefinition(contextual) { _, _ -> },
        )
        fun encode(definition: IsValueDefinition<T, IsPropertyContext>): ByteArray {
            val cache = WriteCache()
            val bytes = mutableListOf<Byte>()
            definition.calculateTransportByteLengthWithKey(1, value, cache)
            definition.writeTransportBytesWithKey(1, value, cache, bytes::add)
            return bytes.toByteArray()
        }
        val expected = encode(resolved)
        for (definition in wrappers) {
            val bytes = encode(definition)
            assertContentEquals(expected, bytes, definition.toString())
            var offset = 0
            assertEquals(resolved.wireType, ProtoBuf.readKey { bytes[offset++] }.wireType)
            for (wireType in listOf(VAR_INT, BIT_32, BIT_64, LENGTH_DELIMITED)) {
                assertEquals(wireType == resolved.wireType, definition.acceptsProtoBufWireType(wireType, null), definition.toString())
            }
        }
    }

    @Test
    fun contextualCollectionEnvelopesCheckTheirInnerWireTypes() {
        val definition = ContextualPropertyValueDefinition(contextualResolver = { _: IsPropertyContext? ->
            ListDefinition(valueDefinition = StringDefinition())
        })
        val malformed = byteArrayOf(0x0d, 'a'.code.toByte(), 'b'.code.toByte(), 'c'.code.toByte(), 'd'.code.toByte())
        var offset = 0
        assertFailsWith<ParseException> { definition.readTransportBytes(malformed.size, { malformed[offset++] }) }
    }
}
