package maryk.core.properties.definitions.contextual

import maryk.SimpleMarykObject
import maryk.checkProtoBufConversion
import maryk.core.models.SimpleObjectDataModel
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.test.ByteCollector
import maryk.test.shouldBe
import kotlin.test.Test

class ContextualEmbeddedObjectDefinitionTest {
    private val subModelsToTest = listOf(
        SimpleMarykObject("test1"),
        SimpleMarykObject("test2")
    )

    @Suppress("UNCHECKED_CAST")
    private val def = ContextualEmbeddedObjectDefinition<ModelContext>(
        contextualResolver = { it!!.model!!.invoke(Unit) as SimpleObjectDataModel<Any, ObjectPropertyDefinitions<Any>> }
    )

    private val context = ModelContext(
        definitionsContext = null,
        model = { SimpleMarykObject }
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
