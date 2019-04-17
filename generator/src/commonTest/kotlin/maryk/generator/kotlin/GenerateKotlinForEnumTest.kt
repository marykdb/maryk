package maryk.generator.kotlin

import maryk.test.models.MarykEnum
import maryk.test.shouldBe
import kotlin.test.Test

val generatedKotlinForEnum = """
package maryk.test.models

import maryk.core.properties.enum.IndexedEnumDefinition
import maryk.core.properties.enum.IndexedEnumImpl

sealed class MarykEnum(
    index: UInt
) : IndexedEnumImpl<MarykEnum>(index) {
    object O1: MarykEnum(1u)
    object O2: MarykEnum(2u)
    object O3: MarykEnum(3u)

    class UnknownMarykEnum(index: UInt, override val name: String): MarykEnum(index)

    companion object : IndexedEnumDefinition<MarykEnum>(
        MarykEnum::class,
        values = { arrayOf(O1, O2, O3) },
        reservedIndices = listOf(99u),
        reservedNames = listOf("O99"),
        unknownCreator = ::UnknownMarykEnum
    )
}
""".trimIndent()

class GenerateKotlinForEnumTest {
    @Test
    fun generateKotlinForSimpleModel() {
        var output = ""

        MarykEnum.generateKotlin("maryk.test.models") {
            output += it
        }

        output shouldBe generatedKotlinForEnum
    }
}
