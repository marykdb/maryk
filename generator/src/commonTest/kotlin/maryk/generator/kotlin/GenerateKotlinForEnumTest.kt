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
    object T1: MarykTypeEnum(1u, setOf("Type1"))
    object T2: MarykTypeEnum(2u)
    object T3: MarykTypeEnum(3u)
    object T4: MarykTypeEnum(4u)
    object T5: MarykTypeEnum(5u)
    object T6: MarykTypeEnum(6u)
    object T7: MarykTypeEnum(7u)

    class UnknownMarykTypeEnum(index: UInt, override val name: String): MarykTypeEnum(index)

    companion object : IndexedEnumDefinition<MarykTypeEnum>(
        MarykTypeEnum::class,
        values = { arrayOf(T1, T2, T3, T4, T5, T6, T7) },
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
