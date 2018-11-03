package maryk.core.properties.definitions.contextual

import maryk.SimpleMarykModel
import maryk.checkProtoBufConversion
import maryk.core.models.AbstractValuesDataModel
import maryk.core.models.IsValuesDataModel
import maryk.core.objects.ValuesImpl
import maryk.core.properties.PropertyDefinitions
import maryk.test.ByteCollector
import maryk.test.shouldBe
import kotlin.test.Test

class ContextualEmbeddedValuesDefinitionTest {
    private val subModelsToTest = listOf(
        SimpleMarykModel("test1"),
        SimpleMarykModel("test2")
    )

    @Suppress("UNCHECKED_CAST")
    private val def = ContextualEmbeddedValuesDefinition<ModelContext>(
        contextualResolver = { it!!.model!!.invoke(Unit) as AbstractValuesDataModel<IsValuesDataModel<PropertyDefinitions>, PropertyDefinitions, ModelContext> }
    )

    private val context = ModelContext(
        definitionsContext = null,
        model = { SimpleMarykModel }
    )

    @Test
    fun testTransportConversion() {
        val bc = ByteCollector()
        for (value in subModelsToTest) {
            @Suppress("UNCHECKED_CAST")
            checkProtoBufConversion(
                bc,
                value as ValuesImpl,
                this.def,
                this.context
            )
        }
    }

    @Test
    fun convertString() {
        for (it in subModelsToTest) {
            @Suppress("UNCHECKED_CAST")
            val b = def.asString(
                it as ValuesImpl,
                this.context
            )
            def.fromString(b, this.context) shouldBe it
        }
    }
}
