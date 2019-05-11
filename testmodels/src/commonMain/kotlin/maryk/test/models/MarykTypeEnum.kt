package maryk.test.models

import maryk.core.properties.enum.IndexedEnumDefinition
import maryk.core.properties.enum.IndexedEnumImpl
import maryk.core.properties.enum.TypeEnum
import maryk.core.values.Values
import maryk.test.models.EmbeddedMarykModel.Properties

sealed class MarykTypeEnum<T: Any>(
    index: UInt,
    alternativeNames: Set<String>? = null
) : IndexedEnumImpl<MarykTypeEnum<*>>(index, alternativeNames), TypeEnum<T> {
    object O1: MarykTypeEnum<String>(1u, setOf("Object1"))
    object O2: MarykTypeEnum<Short>(2u)
    object O3: MarykTypeEnum<Values<EmbeddedMarykModel, Properties>>(3u)
    object O4: MarykTypeEnum<List<String>>(4u)
    class UnknownMarykTypeEnum(index: UInt, override val name: String): MarykTypeEnum<Any>(index)

    companion object : IndexedEnumDefinition<MarykTypeEnum<*>>(
        MarykTypeEnum::class,
        values = { arrayOf(O1, O2, O3, O4) },
        reservedIndices = listOf(99u),
        reservedNames = listOf("O99"),
        unknownCreator = ::UnknownMarykTypeEnum
    )
}
