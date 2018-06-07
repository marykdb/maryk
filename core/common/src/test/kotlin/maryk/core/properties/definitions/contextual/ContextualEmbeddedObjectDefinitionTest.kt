package maryk.core.properties.definitions.contextual

import maryk.SimpleMarykObject
import maryk.checkProtoBufConversion
import maryk.core.objects.RootDataModel
import maryk.core.properties.ByteCollector
import maryk.core.properties.definitions.PropertyDefinitions
import maryk.core.query.DataModelPropertyContext
import maryk.test.shouldBe
import kotlin.test.Test

class ContextualEmbeddedObjectDefinitionTest {
    private val subModelsToTest = listOf(
        SimpleMarykObject("test1"),
        SimpleMarykObject("test2")
    )

    private val def = ContextualEmbeddedObjectDefinition<DataModelPropertyContext>(
        contextualResolver = { it!!.dataModel!! }
    )

    @Suppress("UNCHECKED_CAST")
    private val context = DataModelPropertyContext(
        mapOf(
            SimpleMarykObject.name to { SimpleMarykObject }
        ),
        dataModel = SimpleMarykObject as RootDataModel<Any, PropertyDefinitions<Any>>
    )

    @Test
    fun testTransportConversion() {
        val bc = ByteCollector()
        for (value in subModelsToTest) {
            checkProtoBufConversion(bc, value, this.def, this.context)
        }
    }

    @Test
    fun convertString() {
        for (it in subModelsToTest) {
            val b = def.asString(it, this.context)
            def.fromString(b, this.context) shouldBe it
        }
    }
}
