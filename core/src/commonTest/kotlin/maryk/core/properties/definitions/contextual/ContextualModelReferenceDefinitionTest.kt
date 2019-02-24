package maryk.core.properties.definitions.contextual

import maryk.checkProtoBufConversion
import maryk.core.extensions.toUnitLambda
import maryk.core.models.IsNamedDataModel
import maryk.core.models.ObjectDataModel
import maryk.core.query.RequestContext
import maryk.test.ByteCollector
import maryk.test.models.EmbeddedMarykModel
import maryk.test.models.EmbeddedMarykObject
import maryk.test.models.TestMarykModel
import maryk.test.models.TestMarykObject
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
    private val def = ContextualModelReferenceDefinition<IsNamedDataModel<*>, RequestContext>(
        contextualResolver = { context, name -> context!!.dataModels[name] as Unit.() -> ObjectDataModel<*, *> }
    )

    private val context = RequestContext(
        dataModels = mapOf(
            TestMarykObject.name toUnitLambda { TestMarykObject },
            EmbeddedMarykObject.name toUnitLambda { EmbeddedMarykObject },
            TestMarykModel.name toUnitLambda { TestMarykModel },
            EmbeddedMarykModel.name toUnitLambda { EmbeddedMarykModel }
        )
    )

    @Test
    fun testTransportConversion() {
        val bc = ByteCollector()
        for (value in modelsToTest) {
            checkProtoBufConversion(
                bc,
                DataModelReference(value.name) { value },
                this.def,
                this.context
            ) { converted, original ->
                converted.get(Unit) shouldBe original.get(Unit)
            }
        }
    }

    @Test
    fun convertString() {
        for (it in modelsToTest) {
            val b = def.asString(DataModelReference(it.name) { it }, this.context)
            def.fromString(b, this.context).get.invoke(Unit) shouldBe it
        }
    }
}
