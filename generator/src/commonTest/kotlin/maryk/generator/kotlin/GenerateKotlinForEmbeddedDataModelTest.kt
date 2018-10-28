package maryk.generator.kotlin

import maryk.EmbeddedModel
import maryk.test.shouldBe
import kotlin.test.Test

val generatedKotlinForEmbeddedDataModel = """
package maryk

import maryk.core.models.DataModel
import maryk.core.properties.PropertyDefinitions
import maryk.core.properties.definitions.StringDefinition

object EmbeddedModel : DataModel<EmbeddedModel, EmbeddedModel.Properties>(
    name = "EmbeddedModel",
    properties = Properties
) {
    object Properties: PropertyDefinitions() {
        val value = add(
            index = 1, name = "value",
            definition = StringDefinition(
                default = "haha",
                regEx = "ha.*"
            )
        )
    }

    operator fun invoke(
        value: String = "haha"
    ) = map {
        mapNonNulls(
            this.value with value
        )
    }
}
""".trimIndent()

class GenerateKotlinForEmbeddedDataModelTest {
    @Test
    fun generate_kotlin_for_simple_model(){
        var output = ""

        EmbeddedModel.generateKotlin("maryk") {
            output += it
        }

        output shouldBe generatedKotlinForEmbeddedDataModel
    }
}
