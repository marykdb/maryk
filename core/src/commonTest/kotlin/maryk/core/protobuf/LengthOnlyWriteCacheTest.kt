package maryk.core.protobuf

import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.IsSerializablePropertyDefinition
import maryk.core.properties.definitions.ListDefinition
import maryk.core.properties.definitions.MapDefinition
import maryk.core.properties.definitions.NumberDefinition
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.types.numeric.Float64
import maryk.core.properties.types.numeric.UInt32
import maryk.core.query.DefinitionsContext
import maryk.core.query.RequestContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class LengthOnlyWriteCacheTest {
    @Test
    fun sizingMatchesWrittenBytesAndNestedCacheOrder() {
        val strings = ListDefinition(valueDefinition = StringDefinition())
        check(strings, listOf("alpha", "β", "🙂", ""))
        check(ListDefinition(valueDefinition = NumberDefinition(type = UInt32)), listOf(0u, 127u, 128u, UInt.MAX_VALUE))
        check(ListDefinition(valueDefinition = NumberDefinition(type = Float64)), listOf(1.25, -4.0))
        check(MapDefinition(keyDefinition = StringDefinition(), valueDefinition = StringDefinition()), mapOf("a" to "value", "β" to ""))
        check(MapDefinition(keyDefinition = StringDefinition(), valueDefinition = strings), mapOf("a" to listOf("one", "two"), "β" to emptyList()))
    }

    private fun <T : Any> check(definition: IsSerializablePropertyDefinition<T, IsPropertyContext>, value: T) {
        fun context(): IsPropertyContext = RequestContext(DefinitionsContext())
        val cache = WriteCache()
        val size = definition.calculateTransportByteLengthWithKey(1, value, cache, context())
        assertEquals(size, definition.calculateTransportByteLengthWithKey(1, value, LengthOnlyWriteCache, context()))
        val bytes = ByteArray(size)
        var offset = 0
        definition.writeTransportBytesWithKey(1, value, cache, { bytes[offset++] = it }, context())
        assertEquals(size, offset)
        offset = 0
        val reader = { bytes[offset++] }
        var decoded: T? = null
        while (offset < bytes.size) {
            val key = ProtoBuf.readKey(reader)
            val length = ProtoBuf.getLength(key.wireType, reader)
            decoded = definition.readTransportBytes(length, reader, context(), decoded)
        }
        assertEquals(value, decoded)
    }

    @Test
    fun sizingKeepsLengthOverflowChecks() {
        for (cache in listOf<WriteCacheWriter>(WriteCache(), LengthOnlyWriteCache)) {
            for (length in listOf(-1, Int.MAX_VALUE)) {
                assertFailsWith<IllegalArgumentException> {
                    calculateKeyAndContentLength(WireType.LENGTH_DELIMITED, 1u, cache) { length }
                }
            }
        }
    }
}
