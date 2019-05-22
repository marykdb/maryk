package maryk.generator.kotlin

import maryk.test.models.MarykTypeEnum
import maryk.test.shouldBe
import kotlin.test.Test

val generatedKotlinForTypeEnum = """
package maryk.test.models

import maryk.core.properties.definitions.EmbeddedValuesDefinition
import maryk.core.properties.definitions.IsUsableInMultiType
import maryk.core.properties.definitions.ListDefinition
import maryk.core.properties.definitions.MapDefinition
import maryk.core.properties.definitions.MultiTypeDefinition
import maryk.core.properties.definitions.NumberDefinition
import maryk.core.properties.definitions.SetDefinition
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.enum.IndexedEnumImpl
import maryk.core.properties.enum.MultiTypeEnum
import maryk.core.properties.enum.MultiTypeEnumDefinition
import maryk.core.properties.types.TypedValue
import maryk.core.properties.types.numeric.SInt32
import maryk.core.properties.types.numeric.UInt32
import maryk.core.values.Values

sealed class MarykTypeEnum<T: Any>(
    index: UInt,
    override val definition: IsUsableInMultiType<T, *>?,
    alternativeNames: Set<String>? = null
) : IndexedEnumImpl<MarykTypeEnum<Any>>(index, alternativeNames), MultiTypeEnum<T> {
    object T1: MarykTypeEnum<String>(1u,
        StringDefinition(
            regEx = "[^&]+"
        ),
        setOf("Type1")
    )
    object T2: MarykTypeEnum<Int>(2u,
        NumberDefinition(
            type = SInt32,
            maxValue = 2000
        )
    )
    object T3: MarykTypeEnum<Values<EmbeddedMarykModel, EmbeddedMarykModel.Properties>>(3u,
        EmbeddedValuesDefinition(
            dataModel = { EmbeddedMarykModel }
        )
    )
    object T4: MarykTypeEnum<List<String>>(4u,
        ListDefinition(
            valueDefinition = StringDefinition(
                regEx = "[^&]+"
            )
        )
    )
    object T5: MarykTypeEnum<Set<String>>(5u,
        SetDefinition(
            valueDefinition = StringDefinition(
                regEx = "[^&]+"
            )
        )
    )
    object T6: MarykTypeEnum<Map<UInt, String>>(6u,
        MapDefinition(
            keyDefinition = NumberDefinition(
                type = UInt32
            ),
            valueDefinition = StringDefinition(
                regEx = "[^&]+"
            )
        )
    )
    object T7: MarykTypeEnum<TypedValue<SimpleMarykTypeEnum<out Any>, Any>>(7u,
        MultiTypeDefinition(
            typeEnum = SimpleMarykTypeEnum
        )
    )

    class UnknownMarykTypeEnum(index: UInt, override val name: String): MarykTypeEnum<Any>(index, null)

    companion object : MultiTypeEnumDefinition<MarykTypeEnum<out Any>>(
        MarykTypeEnum::class,
        values = { arrayOf(T1, T2, T3, T4, T5, T6, T7) },
        reservedIndices = listOf(99u),
        reservedNames = listOf("O99"),
        unknownCreator = ::UnknownMarykTypeEnum
    )
}
""".trimIndent()

class GenerateKotlinForTypeEnumTest {
    @Test
    fun generateKotlinForTypeEnum() {
        var output = ""

        MarykTypeEnum.generateKotlin("maryk.test.models") {
            output += it
        }

        output shouldBe generatedKotlinForTypeEnum
    }
}
