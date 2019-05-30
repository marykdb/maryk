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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.expect

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
                assertEquals(original.get(Unit), converted.get(Unit))
            }
        }
    }

    @Test
    fun convertString() {
        for (namedDataModel in modelsToTest) {
            val b = def.asString(DataModelReference(namedDataModel.name) { namedDataModel }, this.context)
            expect(namedDataModel) { def.fromString(b, this.context).get.invoke(Unit) }
        }
    }
}
