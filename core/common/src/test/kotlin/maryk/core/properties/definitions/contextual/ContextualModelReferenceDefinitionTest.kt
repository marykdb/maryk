package maryk.core.properties.definitions.contextual

import maryk.EmbeddedMarykModel
import maryk.EmbeddedMarykObject
import maryk.TestMarykModel
import maryk.TestMarykObject
import maryk.checkProtoBufConversion
import maryk.core.models.IsNamedDataModel
import maryk.core.models.ObjectDataModel
import maryk.core.query.DataModelPropertyContext
import maryk.test.ByteCollector
import maryk.test.shouldBe
import kotlin.test.Test

class ContextualModelReferenceDefinitionTest {
    private val modelsToTest = listOf<IsNamedDataModel<*>>(
        TestMarykObject,
        EmbeddedMarykObject,
        TestMarykModel,
        EmbeddedMarykModel
    )

    @Suppress("UNCHECKED_CAST")
    private val def = ContextualModelReferenceDefinition<IsNamedDataModel<*>, DataModelPropertyContext>(
        contextualResolver = { context, name -> context!!.dataModels[name] as () -> ObjectDataModel<*, *> }
    )

    @Suppress("UNCHECKED_CAST")
    private val context = DataModelPropertyContext(
        mapOf(
            TestMarykObject.name to { TestMarykObject },
            EmbeddedMarykObject.name to { EmbeddedMarykObject },
            TestMarykModel.name to { TestMarykModel },
            EmbeddedMarykModel.name to { EmbeddedMarykModel }
        )
    )

    @Test
    fun testTransportConversion() {
        val bc = ByteCollector()
        for (value in modelsToTest) {
            checkProtoBufConversion(bc, DataModelReference(value.name) { value }, this.def, this.context) { converted, original ->
                converted.get() shouldBe original.get()
            }
        }
    }

    @Test
    fun convertString() {
        for (it in modelsToTest) {
            val b = def.asString(DataModelReference(it.name) { it }, this.context)
            def.fromString(b, this.context).get.invoke() shouldBe it
        }
    }
}
