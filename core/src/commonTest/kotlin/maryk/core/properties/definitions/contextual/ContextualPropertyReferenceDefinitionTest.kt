package maryk.core.properties.definitions.contextual

import maryk.checkProtoBufConversion
import maryk.core.models.BaseDataModel
import maryk.core.query.RequestContext
import maryk.test.ByteCollector
import maryk.test.models.EmbeddedMarykModel
import maryk.test.models.TestMarykModel
import kotlin.test.Test
import kotlin.test.expect

class ContextualPropertyReferenceDefinitionTest {
    private val refsToTest = mapOf(
        TestMarykModel { string::ref } to "string",
        TestMarykModel { embeddedValues { value::ref } } to "embeddedValues.value"
    )

    private val def = ContextualPropertyReferenceDefinition<RequestContext>(
        contextualResolver = { it!!.dataModel as BaseDataModel<*> }
    )

    private val context = RequestContext(
        mapOf(
            TestMarykModel.Meta.name to DataModelReference(TestMarykModel),
            EmbeddedMarykModel.Meta.name to DataModelReference(EmbeddedMarykModel),
        ),
        dataModel = TestMarykModel
    )

    @Test
    fun testTransportConversion() {
        val bc = ByteCollector()
        for ((toCompare) in refsToTest) {
            checkProtoBufConversion(bc, toCompare, this.def, this.context)
        }
    }

    @Test
    fun convertString() {
        for ((toCompare, value) in refsToTest) {
            val b = def.asString(toCompare, this.context)
            expect(value) { b }
            expect(toCompare) { def.fromString(b, this.context) }
        }
    }
}
