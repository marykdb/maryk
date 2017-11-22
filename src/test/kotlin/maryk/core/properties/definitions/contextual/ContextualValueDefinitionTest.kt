package maryk.core.properties.definitions.contextual

import maryk.SubMarykObject
import maryk.TestMarykObject
import maryk.checkProtoBufConversion
import maryk.core.objects.RootDataModel
import maryk.core.properties.ByteCollectorWithLengthCacher
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.AbstractValueDefinition
import maryk.core.properties.references.IsPropertyReference
import maryk.core.query.DataModelPropertyContext
import maryk.test.shouldBe
import kotlin.test.Test

class ContextualValueDefinitionTest {
    private val valuesToTest = listOf(
            "test1",
            "test2"
    )

    private val def = ContextualValueDefinition<DataModelPropertyContext>(
            index = 12,
            name = "test",
            contextualResolver = { it!!.reference!!.propertyDefinition }
    )

    @Suppress("UNCHECKED_CAST")
    private val context = DataModelPropertyContext(
            mapOf(
                    TestMarykObject.name to TestMarykObject,
                    SubMarykObject.name to SubMarykObject
            ),
            dataModel = TestMarykObject as RootDataModel<Any>,
            reference = TestMarykObject.Properties.string.getRef() as IsPropertyReference<Any, AbstractValueDefinition<Any, IsPropertyContext>>
    )

    @Test
    fun testTransportConversion() {
        val bc = ByteCollectorWithLengthCacher()
        valuesToTest.forEach { value ->
            checkProtoBufConversion(bc, value, this.def, this.context)
        }
    }

    @Test
    fun convertString() {
        valuesToTest.forEach {
            val b = def.asString(it, this.context)
            def.fromString(b, this.context) shouldBe it
        }
    }
}