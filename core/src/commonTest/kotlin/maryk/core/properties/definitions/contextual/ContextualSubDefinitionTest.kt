package maryk.core.properties.definitions.contextual

import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.IsSubDefinition
import maryk.core.properties.definitions.StringDefinition
import maryk.core.protobuf.ProtoBuf
import maryk.core.protobuf.WriteCache
import maryk.test.ByteCollector
import maryk.test.shouldBe
import kotlin.test.Test

private class SubContext : IsPropertyContext {
    val valueDefinition = StringDefinition()
}

class ContextualSubDefinitionTest {
    private val valuesToTest = listOf(
        "test1",
        "test2"
    )

    @Suppress("UNCHECKED_CAST")
    private val def = ContextualSubDefinition(
        contextualResolver = { context: SubContext? ->
            context!!.valueDefinition as IsSubDefinition<String, IsPropertyContext>
        }
    )

    private val context = SubContext()

    @Test
    fun testTransportConversion() {
        val bc = ByteCollector()
        for (value in valuesToTest) {
            val cache = WriteCache()

            bc.reserve(
                def.calculateTransportByteLengthWithKey(22u, value, cache, context)
            )
            def.writeTransportBytesWithKey(22u, value, cache, bc::write, context)
            val key = ProtoBuf.readKey(bc::read)
            key.tag shouldBe 22u

            val converted = def.readTransportBytes(
                ProtoBuf.getLength(key.wireType, bc::read),
                bc::read,
                context
            )

            converted shouldBe value
            bc.reset()
        }
    }
}
