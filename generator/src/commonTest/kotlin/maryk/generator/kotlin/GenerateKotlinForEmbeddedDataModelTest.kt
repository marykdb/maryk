package maryk.generator.kotlin

import maryk.test.models.EmbeddedModel
import kotlin.test.Test
import kotlin.test.assertEquals

val generatedKotlinForEmbeddedDataModel = """
package maryk.test.models

import maryk.core.models.DataModel
import maryk.core.properties.PropertyDefinitions
import maryk.core.properties.definitions.StringDefinition

object EmbeddedModel : DataModel<EmbeddedModel, EmbeddedModel.Properties>(
    properties = Properties
) {
    object Properties : PropertyDefinitions() {
        val value = add(
            index = 1u, name = "value",
            definition = StringDefinition(
                default = "haha",
                regEx = "ha.*"
            )
        )
    }

    operator fun invoke(
        value: String = "haha"
    ) = values {
        mapNonNulls(
            this.value with value
        )
    }
}
""".trimIndent()

class GenerateKotlinForEmbeddedDataModelTest {
    @Test
    fun generateKotlinForSimpleModel() {
        var output = ""

        EmbeddedModel.generateKotlin("maryk.test.models") {
            output += it
        }

        assertEquals(generatedKotlinForEmbeddedDataModel, output)
    }
}
