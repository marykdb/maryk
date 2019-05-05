package maryk.core.properties.definitions.contextual

import maryk.checkProtoBufConversion
import maryk.core.extensions.toUnitLambda
import maryk.core.properties.AbstractPropertyDefinitions
import maryk.core.query.RequestContext
import maryk.test.ByteCollector
import maryk.test.models.EmbeddedMarykModel
import maryk.test.models.TestMarykModel
import maryk.test.shouldBe
import kotlin.test.Test

class ContextualPropertyReferenceDefinitionTest {
    private val refsToTest = mapOf(
        TestMarykModel { string::ref } to "string",
        TestMarykModel { embeddedValues { value::ref } } to "embeddedValues.value"
    )

    private val def = ContextualPropertyReferenceDefinition<RequestContext>(
        contextualResolver = { it!!.dataModel!!.properties as AbstractPropertyDefinitions<*> }
    )

    private val context = RequestContext(
        mapOf(
            TestMarykModel.name toUnitLambda { TestMarykModel },
            EmbeddedMarykModel.name toUnitLambda { EmbeddedMarykModel }
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
            b shouldBe value
            def.fromString(b, this.context) shouldBe toCompare
        }
    }
}
