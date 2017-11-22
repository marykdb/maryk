package maryk.core.properties.definitions.contextual

import maryk.SubMarykObject
import maryk.checkProtoBufConversion
import maryk.core.objects.RootDataModel
import maryk.core.properties.ByteCollectorWithLengthCacher
import maryk.core.query.DataModelPropertyContext
import maryk.test.shouldBe
import kotlin.test.Test

class ContextualSubModelDefinitionTest {
    private val subModelsToTest = listOf(
            SubMarykObject("test1"),
            SubMarykObject("test2")
    )

    private val def = ContextualSubModelDefinition<DataModelPropertyContext>(
            index = 12,
            name = "test",
            contextualResolver = { it!!.dataModel!! }
    )

    @Suppress("UNCHECKED_CAST")
    private val context = DataModelPropertyContext(
            mapOf(
                    SubMarykObject.name to SubMarykObject
            ),
            dataModel = SubMarykObject as RootDataModel<Any>
    )

    @Test
    fun testTransportConversion() {
        val bc = ByteCollectorWithLengthCacher()
        subModelsToTest.forEach { value ->
            checkProtoBufConversion(bc, value, this.def, this.context)
        }
    }

    @Test
    fun convertString() {
        subModelsToTest.forEach {
            val b = def.asString(it, this.context)
            def.fromString(b, this.context) shouldBe it
        }
    }
}