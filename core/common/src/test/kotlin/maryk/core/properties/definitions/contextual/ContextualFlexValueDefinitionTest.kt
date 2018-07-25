package maryk.core.properties.definitions.contextual

import maryk.EmbeddedMarykModel
import maryk.TestMarykModel
import maryk.checkProtoBufConversion
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.IsValueDefinition
import maryk.core.properties.definitions.wrapper.PropertyDefinitionWrapper
import maryk.core.properties.references.IsPropertyReference
import maryk.core.query.DataModelPropertyContext
import maryk.test.ByteCollector
import maryk.test.shouldBe
import kotlin.test.Test

class ContextualValueDefinitionTest {
    private val valuesToTest = listOf(
        "test1",
        "test2"
    )

    @Suppress("UNCHECKED_CAST")
    private val def = ContextualValueDefinition(
        contextualResolver = { context: DataModelPropertyContext? ->
            context!!.reference!!.propertyDefinition.definition as IsValueDefinition<Any, IsPropertyContext>
        }
    )

    private val context = DataModelPropertyContext(
        mapOf(
            TestMarykModel.name to { TestMarykModel },
            EmbeddedMarykModel.name to { EmbeddedMarykModel }
        ),
        dataModel = TestMarykModel,
        reference = TestMarykModel.ref { string } as IsPropertyReference<*, PropertyDefinitionWrapper<*, *, *, *, *>>
    )

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
