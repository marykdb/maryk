package maryk.core.properties.definitions.contextual

import maryk.checkProtoBufConversion
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.IsValueDefinition
import maryk.core.properties.definitions.StringDefinition
import maryk.test.ByteCollector
import kotlin.test.Test
import kotlin.test.expect

private class ValueContext : IsPropertyContext {
    val valueDefinition = StringDefinition()
}

class ContextualValueDefinitionTest {
    private val valuesToTest = listOf(
        "test1",
        "test2"
    )

    @Suppress("UNCHECKED_CAST")
    private val def = ContextualValueDefinition(
        contextualResolver = { context: ValueContext? ->
            context!!.valueDefinition as IsValueDefinition<Any, IsPropertyContext>
        }
    )

    private val context = ValueContext()

    @Test
    fun testTransportConversion() {
        val bc = ByteCollector()
        for (value in valuesToTest) {
            checkProtoBufConversion(bc, value, this.def, this.context)
        }
    }

    @Test
    fun convertString() {
        for (value in valuesToTest) {
            val b = def.asString(value, this.context)
            expect(value) { def.fromString(b, this.context) }
        }
    }
}
