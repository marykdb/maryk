package maryk.generator.kotlin

import maryk.core.models.DataModel
import maryk.core.properties.definitions.string
import maryk.test.models.EmbeddedMarykModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

val generatedKotlinForDataModel = """
package maryk.test.models

import maryk.core.models.DataModel
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

    @Test
    fun generateKotlinRetainsSensitivePropertyFlag() {
        val output = buildString {
            SensitiveMarykModel.generateKotlin("maryk.test.models") {
                append(it)
            }
        }

        assertEquals(
            """
            package maryk.test.models

            import maryk.core.models.DataModel
            import maryk.core.properties.definitions.string

            object SensitiveMarykModel : DataModel<SensitiveMarykModel>() {
                val secret by string(
                    index = 1u,
                    sensitive = true
                )
            }
            """.trimIndent(),
            output,
        )
    }

    @Test
    fun generateKotlinRejectsInvalidPackageBeforeWriting() {
        var writes = 0

        val exception = assertFailsWith<IllegalArgumentException> {
            EmbeddedMarykModel.generateKotlin("maryk.invalid-package") { writes++ }
        }

        assertEquals("Kotlin package name is invalid: maryk.invalid-package", exception.message)
        assertEquals(0, writes)
    }
}

private object SensitiveMarykModel : DataModel<SensitiveMarykModel>() {
    val secret by string(
        index = 1u,
        sensitive = true,
    )
}
