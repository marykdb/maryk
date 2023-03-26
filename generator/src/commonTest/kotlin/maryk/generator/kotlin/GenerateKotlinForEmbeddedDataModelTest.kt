package maryk.generator.kotlin

import maryk.test.models.EmbeddedModel
import kotlin.test.Test
import kotlin.test.assertEquals

val generatedKotlinForEmbeddedDataModel = """
package maryk.test.models

import maryk.core.properties.Model
import maryk.core.properties.definitions.string

object EmbeddedModel : Model<EmbeddedModel>() {
    val value by string(
        index = 1u,
        default = "haha",
        regEx = "ha.*"
    )
}
""".trimIndent()

class GenerateKotlinForEmbeddedDataModelTest {
    @Test
    fun generateKotlinForSimpleModel() {
        val output = buildString {
            EmbeddedModel.generateKotlin("maryk.test.models") {
                append(it)
            }
        }

        assertEquals(generatedKotlinForEmbeddedDataModel, output)
    }
}
