package maryk.core.properties.definitions.contextual

import maryk.checkProtoBufConversion
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.IsValueDefinition
import maryk.core.properties.definitions.StringDefinition
import maryk.test.ByteCollector
import maryk.test.shouldBe
import kotlin.test.Test

private class ValueContext: IsPropertyContext {
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
        for (it in valuesToTest) {
            val b = def.asString(it, this.context)
            def.fromString(b, this.context) shouldBe it
        }
    }
}
