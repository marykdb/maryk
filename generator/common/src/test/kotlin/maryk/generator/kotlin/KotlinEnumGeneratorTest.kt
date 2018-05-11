package maryk.generator.kotlin

import maryk.MarykEnum
import maryk.test.shouldBe
import kotlin.test.Test

val generatedKotlinForEnum = """
package maryk

import maryk.core.properties.types.IndexedEnum
import maryk.core.properties.types.IndexedEnumDefinition

enum class MarykEnum(
    override val index: Int
): IndexedEnum<MarykEnum> {
    O1(1),
    O2(2),
    O3(3);

    companion object: IndexedEnumDefinition<MarykEnum>(
        "MarykEnum", MarykEnum::values
    )
}
""".trimIndent()

class KotlinEnumGeneratorTest {
    @Test
    fun generate_kotlin_for_simple_model(){
        var output = ""

        MarykEnum.generateKotlin("maryk") {
            output += it
        }

        output shouldBe generatedKotlinForEnum
    }
}
