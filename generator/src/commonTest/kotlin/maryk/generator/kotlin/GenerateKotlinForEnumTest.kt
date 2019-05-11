package maryk.generator.kotlin

import maryk.test.models.MarykTypeEnum
import maryk.test.shouldBe
import kotlin.test.Test

val generatedKotlinForEnum = """
package maryk.test.models

import maryk.core.properties.enum.IndexedEnumDefinition
import maryk.core.properties.enum.IndexedEnumImpl

sealed class MarykTypeEnum(
    index: UInt,
    alternativeNames: Set<String>? = null
) : IndexedEnumImpl<MarykTypeEnum>(index, alternativeNames) {
    object O1: MarykTypeEnum(1u, setOf("Object1"))
    object O2: MarykTypeEnum(2u)
    object O3: MarykTypeEnum(3u)
    object O4: MarykTypeEnum(4u)

    class UnknownMarykTypeEnum(index: UInt, override val name: String): MarykTypeEnum(index)

    companion object : IndexedEnumDefinition<MarykTypeEnum>(
        MarykTypeEnum::class,
        values = { arrayOf(O1, O2, O3, O4) },
        reservedIndices = listOf(99u),
        reservedNames = listOf("O99"),
        unknownCreator = ::UnknownMarykTypeEnum
    )
}
""".trimIndent()

class GenerateKotlinForEnumTest {
    @Test
    fun generateKotlinForSimpleModel() {
        var output = ""

        MarykTypeEnum.generateKotlin("maryk.test.models") {
            output += it
        }

        output shouldBe generatedKotlinForEnum
    }
}
