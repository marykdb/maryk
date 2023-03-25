package maryk.core.properties.definitions.contextual

import maryk.checkProtoBufConversion
import maryk.core.extensions.toUnitLambda
import maryk.core.models.IsNamedDataModel
import maryk.core.models.ObjectDataModel
import maryk.core.properties.IsPropertyDefinitions
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
    private val modelsToTest = listOf(
        TestMarykObject,
        EmbeddedMarykObject,
        TestMarykModel,
        EmbeddedMarykModel,
    )

    @Suppress("UNCHECKED_CAST")
    private val def = ContextualModelReferenceDefinition<IsPropertyDefinitions, RequestContext>(
        contextualResolver = { context, name -> context!!.dataModels[name] as Unit.() -> IsPropertyDefinitions }
    )

    private val context = RequestContext(
        dataModels = mapOf(
            TestMarykObject.Model.name toUnitLambda { TestMarykObject },
            EmbeddedMarykObject.Model.name toUnitLambda { EmbeddedMarykObject },
            TestMarykModel.Model.name toUnitLambda { TestMarykModel },
            EmbeddedMarykModel.Model.name toUnitLambda { EmbeddedMarykModel }
        )
    )

    @Test
    fun testTransportConversion() {
        val bc = ByteCollector()
        for (value in modelsToTest) {
            checkProtoBufConversion(
                bc,
                DataModelReference((value.Model as IsNamedDataModel<*>).name) { value },
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
            val b = def.asString(DataModelReference((namedDataModel.Model as IsNamedDataModel<*>).name) { namedDataModel }, this.context)
            expect(namedDataModel) { def.fromString(b, this.context).get.invoke(Unit) }
        }
    }
}
