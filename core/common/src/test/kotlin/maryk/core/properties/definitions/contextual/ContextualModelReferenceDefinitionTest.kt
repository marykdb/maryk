package maryk.core.properties.definitions.contextual

import maryk.EmbeddedMarykObject
import maryk.TestMarykObject
import maryk.checkProtoBufConversion
import maryk.core.models.DataModel
import maryk.core.properties.ByteCollector
import maryk.core.query.DataModelPropertyContext
import maryk.test.shouldBe
import kotlin.test.Test

class ContextualModelReferenceDefinitionTest {
    private val modelsToTest = listOf<DataModel<*, *>>(
        TestMarykObject,
        EmbeddedMarykObject
    )

    private val def = ContextualModelReferenceDefinition<DataModel<*, *>, DataModelPropertyContext>(
        contextualResolver = { context, name -> context!!.dataModels[name]!! }
    )

    @Suppress("UNCHECKED_CAST")
    private val context = DataModelPropertyContext(
        mapOf(
            TestMarykObject.name to { TestMarykObject },
            EmbeddedMarykObject.name to { EmbeddedMarykObject }
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
