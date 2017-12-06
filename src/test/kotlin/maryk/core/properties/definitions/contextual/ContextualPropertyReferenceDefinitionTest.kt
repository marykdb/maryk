package maryk.core.properties.definitions.contextual

import maryk.SubMarykObject
import maryk.TestMarykObject
import maryk.checkProtoBufConversion
import maryk.core.objects.RootDataModel
import maryk.core.properties.ByteCollectorWithLengthCacher
import maryk.core.query.DataModelPropertyContext
import maryk.test.shouldBe
import kotlin.test.Test

class ContextualPropertyReferenceDefinitionTest {
    private val refsToTest = listOf(
            TestMarykObject.Properties.string.getRef(),
            SubMarykObject.Properties.value.getRef(
                    { TestMarykObject.Properties.subModel.getRef() }
            )
    )

    private val def = ContextualPropertyReferenceDefinition<DataModelPropertyContext>(
            contextualResolver = { it!!.dataModel!! }
    )

    @Suppress("UNCHECKED_CAST")
    private val context = DataModelPropertyContext(
            mapOf(
                    TestMarykObject.name to TestMarykObject,
                    SubMarykObject.name to SubMarykObject
            ),
            dataModel = TestMarykObject as RootDataModel<Any>
    )

    @Test
    fun testTransportConversion() {
        val bc = ByteCollectorWithLengthCacher()
        refsToTest.forEach { value ->
            checkProtoBufConversion(bc, value, this.def, this.context)
        }
    }

    @Test
    fun convertString() {
        refsToTest.forEach {
            val b = def.asString(it, this.context)
            def.fromString(b, this.context) shouldBe it
        }
    }
}