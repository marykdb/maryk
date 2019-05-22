package maryk.generator.kotlin

import maryk.test.models.Option
import maryk.test.shouldBe
import kotlin.test.Test

val generatedKotlinForIndexedEnum = """
package maryk.test.models

import maryk.core.properties.enum.IndexedEnumDefinition
import maryk.core.properties.enum.IndexedEnumImpl

sealed class Option(
    index: UInt,
    alternativeNames: Set<String>? = null
) : IndexedEnumImpl<Option>(index, alternativeNames) {
    object V1: Option(1u)
    object V2: Option(2u, setOf("VERSION2"))
    object V3: Option(3u, setOf("VERSION3"))

    class UnknownOption(index: UInt, override val name: String): Option(index)

    companion object : IndexedEnumDefinition<Option>(
        Option::class,
        values = { arrayOf(V1, V2, V3) },
        reservedIndices = listOf(4u),
        reservedNames = listOf("V4"),
        unknownCreator = ::UnknownOption
    )
}
""".trimIndent()

class GenerateKotlinForIndexedEnumTest {
    @Test
    fun generateKotlinForOption() {
        var output = ""

        Option.generateKotlin("maryk.test.models") {
            output += it
        }

        output shouldBe generatedKotlinForIndexedEnum
    }
}
