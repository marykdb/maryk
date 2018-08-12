package maryk.core.properties.definitions.contextual

import maryk.EmbeddedMarykModel
import maryk.TestMarykModel
import maryk.checkProtoBufConversion
import maryk.core.properties.AbstractPropertyDefinitions
import maryk.core.query.RequestContext
import maryk.test.ByteCollector
import maryk.test.shouldBe
import kotlin.test.Test

class ContextualPropertyReferenceDefinitionTest {
    private val refsToTest = listOf(
        TestMarykModel.ref { string },
        TestMarykModel { embeddedValues ref { value } }
    )

    private val def = ContextualPropertyReferenceDefinition<RequestContext>(
        contextualResolver = { it!!.dataModel!!.properties as AbstractPropertyDefinitions<*> }
    )

    private val context = RequestContext(
        mapOf(
            TestMarykModel.name to { TestMarykModel },
            EmbeddedMarykModel.name to { EmbeddedMarykModel }
        ),
        dataModel = TestMarykModel
    )

    @Test
    fun testTransportConversion() {
        val bc = ByteCollector()
        for (value in refsToTest) {
            checkProtoBufConversion(bc, value, this.def, this.context)
        }
    }

    @Test
    fun convertString() {
        for (it in refsToTest) {
            val b = def.asString(it, this.context)
            def.fromString(b, this.context) shouldBe it
        }
    }
}
