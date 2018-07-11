package maryk.core.properties.definitions.contextual

import maryk.EmbeddedMarykObject
import maryk.TestMarykObject
import maryk.checkProtoBufConversion
import maryk.core.models.RootDataModel
import maryk.core.properties.ByteCollector
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.query.DataModelPropertyContext
import maryk.test.shouldBe
import kotlin.test.Test

class ContextualPropertyReferenceDefinitionTest {
    private val refsToTest = listOf(
        TestMarykObject.ref { string },
        TestMarykObject { embeddedObject ref { value } }
    )

    private val def = ContextualPropertyReferenceDefinition<DataModelPropertyContext>(
        contextualResolver = { it!!.dataModel!!.properties }
    )

    @Suppress("UNCHECKED_CAST")
    private val context = DataModelPropertyContext(
        mapOf(
            TestMarykObject.name to { TestMarykObject },
            EmbeddedMarykObject.name to { EmbeddedMarykObject }
        ),
        dataModel = TestMarykObject as RootDataModel<Any, ObjectPropertyDefinitions<Any>>
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
