package maryk.test.models

import maryk.core.properties.enum.IndexedEnumDefinition
import maryk.core.properties.enum.IndexedEnumImpl
import maryk.core.properties.enum.MultiTypeEnum
import maryk.core.properties.types.TypedValue
import maryk.core.values.Values
import maryk.test.models.EmbeddedMarykModel.Properties

sealed class MarykTypeEnum<T: Any>(
    index: UInt,
    alternativeNames: Set<String>? = null
) : IndexedEnumImpl<MarykTypeEnum<*>>(index, alternativeNames), MultiTypeEnum<T> {
    object T1: MarykTypeEnum<String>(1u, setOf("Type1"))
    object T2: MarykTypeEnum<Int>(2u)
    object T3: MarykTypeEnum<Values<EmbeddedMarykModel, Properties>>(3u)
    object T4: MarykTypeEnum<List<String>>(4u)
    object T5: MarykTypeEnum<Set<String>>(5u)
    object T6: MarykTypeEnum<Map<UInt, String>>(6u)
    object T7: MarykTypeEnum<TypedValue<MarykTypeEnum<*>, Any>>(7u)

    class UnknownMarykTypeEnum(index: UInt, override val name: String): MarykTypeEnum<Any>(index)

    companion object : IndexedEnumDefinition<MarykTypeEnum<*>>(
        MarykTypeEnum::class,
        values = { arrayOf(T1, T2, T3, T4, T5, T6, T7) },
        reservedIndices = listOf(99u),
        reservedNames = listOf("O99"),
        unknownCreator = ::UnknownMarykTypeEnum
    )
}
