package maryk.core.properties.definitions.contextual

import maryk.checkProtoBufConversion
import maryk.core.extensions.toUnitLambda
import maryk.core.models.IsDataModel
import maryk.core.models.IsStorableDataModel
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

    private val def = ContextualModelReferenceDefinition<IsDataModel, RequestContext>(
        contextualResolver = { context, name -> context!!.dataModels[name] as Unit.() -> IsDataModel }
    )

    private val context = RequestContext(
        dataModels = mapOf(
            TestMarykObject.Meta.name toUnitLambda { TestMarykObject },
            EmbeddedMarykObject.Meta.name toUnitLambda { EmbeddedMarykObject },
            TestMarykModel.Meta.name toUnitLambda { TestMarykModel },
            EmbeddedMarykModel.Meta.name toUnitLambda { EmbeddedMarykModel }
        )
    )

    @Test
    fun testTransportConversion() {
        val bc = ByteCollector()
        for (value in modelsToTest) {
            checkProtoBufConversion(
                bc,
                DataModelReference((value as IsStorableDataModel<*>).Meta.name) { value },
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
            val b = def.asString(DataModelReference((namedDataModel as IsStorableDataModel<*>).Meta.name) { namedDataModel }, this.context)
            expect(namedDataModel) { def.fromString(b, this.context).get.invoke(Unit) }
        }
    }
}
