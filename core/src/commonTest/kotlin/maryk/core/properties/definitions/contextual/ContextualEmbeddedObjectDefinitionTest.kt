package maryk.core.properties.definitions.contextual

import maryk.checkProtoBufConversion
import maryk.core.models.IsSimpleBaseObjectDataModel
import maryk.test.ByteCollector
import maryk.test.models.SimpleMarykObject
import kotlin.test.Test
import kotlin.test.expect

class ContextualEmbeddedObjectDefinitionTest {
    private val subModelsToTest = listOf(
        SimpleMarykObject("test1"),
        SimpleMarykObject("test2")
    )

    @Suppress("UNCHECKED_CAST")
    private val def = ContextualEmbeddedObjectDefinition<ModelContext>(
        contextualResolver = { it!!.model!!.invoke(Unit) as IsSimpleBaseObjectDataModel<Any, *, ModelContext> }
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
        for (subModel in subModelsToTest) {
            val b = def.asString(subModel, this.context)
            expect(subModel) { def.fromString(b, this.context) }
        }
    }
}
