package maryk.core.properties.definitions.contextual

import maryk.EmbeddedMarykObject
import maryk.TestMarykObject
import maryk.checkProtoBufConversion
import maryk.core.models.RootDataModel
import maryk.core.properties.ByteCollector
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.IsValueDefinition
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.definitions.wrapper.PropertyDefinitionWrapper
import maryk.core.properties.references.IsPropertyReference
import maryk.core.query.DataModelPropertyContext
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

    @Suppress("UNCHECKED_CAST")
    private val context = DataModelPropertyContext(
        mapOf(
            TestMarykObject.name to { TestMarykObject },
            EmbeddedMarykObject.name to { EmbeddedMarykObject }
        ),
        dataModel = TestMarykObject as RootDataModel<Any, ObjectPropertyDefinitions<Any>>,
        reference = TestMarykObject.ref { string } as IsPropertyReference<*, PropertyDefinitionWrapper<*, *, *, *, *>>
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
