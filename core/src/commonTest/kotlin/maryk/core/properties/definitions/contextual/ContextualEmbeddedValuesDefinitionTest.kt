package maryk.core.properties.definitions.contextual

import maryk.checkProtoBufConversion
import maryk.core.models.AbstractValuesDataModel
import maryk.core.models.IsValuesDataModel
import maryk.core.properties.IsValuesPropertyDefinitions
import maryk.core.values.ValuesImpl
import maryk.test.ByteCollector
import maryk.test.models.SimpleMarykModel
import kotlin.test.Test
import kotlin.test.expect

class ContextualEmbeddedValuesDefinitionTest {
    private val subModelsToTest = listOf(
        SimpleMarykModel.run { create(value with "test1") },
        SimpleMarykModel.run { create(value with "test2") }
    )

    @Suppress("UNCHECKED_CAST")
    private val def = ContextualEmbeddedValuesDefinition<ModelContext>(
        contextualResolver = { it!!.model!!.invoke(Unit) as AbstractValuesDataModel<IsValuesDataModel<IsValuesPropertyDefinitions>, IsValuesPropertyDefinitions, ModelContext> }
    )

    private val context = ModelContext(
        definitionsContext = null,
        model = { SimpleMarykModel.Model }
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
        for (subModel in subModelsToTest) {
            @Suppress("UNCHECKED_CAST")
            val b = def.asString(
                subModel as ValuesImpl,
                this.context
            )
            expect(subModel) { def.fromString(b, this.context) }
        }
    }
}
