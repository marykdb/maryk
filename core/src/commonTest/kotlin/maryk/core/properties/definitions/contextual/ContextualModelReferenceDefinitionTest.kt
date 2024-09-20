package maryk.core.properties.definitions.contextual

import maryk.checkProtoBufConversion
import maryk.core.models.IsDataModel
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
        contextualResolver = { context, name ->
            @Suppress("UNCHECKED_CAST")
            context!!.dataModels[name] as IsDataModelReference<IsDataModel>
        }
    )

    private val context = RequestContext(
        dataModels = mapOf(
            TestMarykObject.Meta.name to DataModelReference(TestMarykObject),
            EmbeddedMarykObject.Meta.name to DataModelReference(EmbeddedMarykObject),
            TestMarykModel.Meta.name to DataModelReference(TestMarykModel),
            EmbeddedMarykModel.Meta.name to DataModelReference(EmbeddedMarykModel),
        )
    )

    @Test
    fun testTransportConversion() {
        val bc = ByteCollector()
        for (value in modelsToTest) {
            checkProtoBufConversion(
                bc,
                DataModelReference(value),
                this.def,
                this.context
            ) { converted, original ->
                assertEquals(original.get(), converted.get())
            }
        }
    }

    @Test
    fun convertString() {
        for (namedDataModel in modelsToTest) {
            val b = def.asString(DataModelReference(namedDataModel), this.context)
            expect(namedDataModel) { def.fromString(b, this.context).get.invoke() }
        }
    }
}
