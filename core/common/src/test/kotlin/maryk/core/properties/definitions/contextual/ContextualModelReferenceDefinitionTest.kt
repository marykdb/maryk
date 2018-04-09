package maryk.core.properties.definitions.contextual

import maryk.SubMarykObject
import maryk.TestMarykObject
import maryk.checkProtoBufConversion
import maryk.core.properties.ByteCollector
import maryk.core.query.DataModelPropertyContext
import maryk.test.shouldBe
import kotlin.test.Test

class ContextualModelReferenceDefinitionTest {
    private val modelsToTest = listOf(
        TestMarykObject,
        SubMarykObject
    )

    private val def = ContextualModelReferenceDefinition<DataModelPropertyContext>(
        contextualResolver = { context, name ->  context!!.dataModels[name]!! }
    )

    @Suppress("UNCHECKED_CAST")
    private val context = DataModelPropertyContext(
        mapOf(
            TestMarykObject.name to TestMarykObject,
            SubMarykObject.name to SubMarykObject
        )
    )

    @Test
    fun testTransportConversion() {
        val bc = ByteCollector()
        for (value in modelsToTest) {
            checkProtoBufConversion(bc, value, this.def, this.context)
        }
    }

    @Test
    fun convertString() {
        for (it in modelsToTest) {
            val b = def.asString(it, this.context)
            def.fromString(b, this.context) shouldBe it
        }
    }
}
