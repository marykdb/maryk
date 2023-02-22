package maryk.core.properties.definitions.contextual

import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.StringDefinition
import maryk.core.protobuf.ProtoBuf
import maryk.core.protobuf.WriteCache
import maryk.test.ByteCollector
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.expect

private class SubContext : IsPropertyContext {
    val valueDefinition = StringDefinition()
}

class ContextualSubDefinitionTest {
    private val valuesToTest = listOf(
        "test1",
        "test2"
    )

    private val def = ContextualSubDefinition(
        contextualResolver = { context: SubContext? ->
            context!!.valueDefinition
        }
    )

    private val context = SubContext()

    @Test
    fun testTransportConversion() {
        val bc = ByteCollector()
        for (value in valuesToTest) {
            val cache = WriteCache()

            bc.reserve(
                def.calculateTransportByteLengthWithKey(22, value, cache, context)
            )
            def.writeTransportBytesWithKey(22, value, cache, bc::write, context)
            val key = ProtoBuf.readKey(bc::read)
            expect(22u) { key.tag }

            val converted = def.readTransportBytes(
                ProtoBuf.getLength(key.wireType, bc::read),
                bc::read,
                context
            )

            assertEquals(value, converted)
            bc.reset()
        }
    }
}
