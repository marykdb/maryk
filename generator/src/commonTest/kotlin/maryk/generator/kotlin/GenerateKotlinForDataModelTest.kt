package maryk.generator.kotlin

import maryk.test.models.EmbeddedMarykModel
import kotlin.test.Test
import kotlin.test.assertEquals

val generatedKotlinForDataModel = """
package maryk.test.models

import maryk.core.models.DataModel
import maryk.core.properties.PropertyDefinitions
import maryk.core.properties.definitions.embed
import maryk.core.properties.definitions.string
import maryk.core.values.Values

object EmbeddedMarykModel : DataModel<EmbeddedMarykModel, EmbeddedMarykModel.Properties>(
    reservedIndices = listOf(999u),
    reservedNames = listOf("reserved"),
    properties = Properties
) {
    object Properties : PropertyDefinitions() {
        val value by string(
            index = 1u
        )
        val model by embed(
            index = 2u,
            required = false,
            dataModel = { EmbeddedMarykModel }
        )
        val marykModel by embed(
            index = 3u,
            required = false,
            dataModel = { TestMarykModel }
        )
    }

    operator fun invoke(
        value: String,
        model: Values<EmbeddedMarykModel, EmbeddedMarykModel.Properties>? = null,
        marykModel: Values<TestMarykModel, TestMarykModel.Properties>? = null
    ) = values {
        mapNonNulls(
            this.value with value,
            this.model with model,
            this.marykModel with marykModel
        )
    }
}
""".trimIndent()

class GenerateKotlinForDataModelTest {
    @Test
    fun generateKotlinForDataModel() {
        val output = buildString {
            EmbeddedMarykModel.Model.generateKotlin("maryk.test.models") {
                append(it)
            }
        }

        assertEquals(generatedKotlinForDataModel, output)
    }
}
