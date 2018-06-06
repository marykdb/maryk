package maryk.core.properties.definitions.contextual

import maryk.SubMarykObject
import maryk.TestMarykObject
import maryk.checkProtoBufConversion
import maryk.core.objects.DataModel
import maryk.core.properties.ByteCollector
import maryk.core.query.DataModelPropertyContext
import maryk.test.shouldBe
import kotlin.test.Test

class ContextualModelReferenceDefinitionTest {
    private val modelsToTest = listOf<DataModel<*, *>>(
        TestMarykObject,
        SubMarykObject
    )

    private val def = ContextualModelReferenceDefinition<DataModel<*, *>, DataModelPropertyContext>(
        contextualResolver = { context, name -> context!!.dataModels[name]!!.invoke() }
    )

    @Suppress("UNCHECKED_CAST")
    private val context = DataModelPropertyContext(
        mapOf(
            TestMarykObject.name to { TestMarykObject },
            SubMarykObject.name to { SubMarykObject }
        )
    )

    @Test
    fun testTransportConversion() {
        val bc = ByteCollector()
        for (value in modelsToTest) {
            checkProtoBufConversion(bc, DataModelReference(value.name){ value }, this.def, this.context) { converted, original ->
                converted.get() shouldBe original.get()
            }
        }
    }

    @Test
    fun convertString() {
        for (it in modelsToTest) {
            val b = def.asString(DataModelReference(it.name){ it }, this.context)
            def.fromString(b, this.context).get.invoke() shouldBe it
        }
    }
}
