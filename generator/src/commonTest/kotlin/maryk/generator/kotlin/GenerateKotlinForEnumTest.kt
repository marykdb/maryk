package maryk.generator.kotlin

import maryk.test.models.MarykEnum
import maryk.test.shouldBe
import kotlin.test.Test

val generatedKotlinForEnum = """
package maryk.test.models

import maryk.core.properties.enum.IndexedEnum
import maryk.core.properties.enum.IndexedEnumDefinition

enum class MarykEnum(
    override val index: UInt
) : IndexedEnum<MarykEnum> {
    O1(1u),
    O2(2u),
    O3(3u);

    companion object : IndexedEnumDefinition<MarykEnum>(
        "MarykEnum", MarykEnum::values
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
