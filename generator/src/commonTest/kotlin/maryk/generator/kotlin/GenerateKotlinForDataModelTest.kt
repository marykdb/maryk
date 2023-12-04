package maryk.generator.kotlin

import maryk.test.models.EmbeddedMarykModel
import kotlin.test.Test
import kotlin.test.assertEquals

val generatedKotlinForDataModel = """
package maryk.test.models

import maryk.core.properties.DataModel
import maryk.core.properties.definitions.embed
import maryk.core.properties.definitions.string
import maryk.core.values.Values

object EmbeddedMarykModel : DataModel<EmbeddedMarykModel>(
    reservedIndices = listOf(999u),
    reservedNames = listOf("reserved"),
) {
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
""".trimIndent()

class GenerateKotlinForDataModelTest {
    @Test
    fun generateKotlinForDataModel() {
        val output = buildString {
            EmbeddedMarykModel.generateKotlin("maryk.test.models") {
                append(it)
            }
        }

        assertEquals(generatedKotlinForDataModel, output)
    }
}
