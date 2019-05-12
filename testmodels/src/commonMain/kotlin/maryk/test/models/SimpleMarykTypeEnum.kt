package maryk.test.models

import maryk.core.properties.enum.IndexedEnumDefinition
import maryk.core.properties.enum.IndexedEnumImpl
import maryk.core.properties.enum.MultiTypeEnum
import maryk.core.values.Values
import maryk.test.models.EmbeddedMarykModel.Properties

sealed class SimpleMarykTypeEnum<T: Any>(
    index: UInt,
    alternativeNames: Set<String>? = null
) : IndexedEnumImpl<SimpleMarykTypeEnum<*>>(index, alternativeNames), MultiTypeEnum<T> {
    object S1: SimpleMarykTypeEnum<String>(1u, setOf("Type1"))
    object S2: SimpleMarykTypeEnum<Short>(2u)
    object S3: SimpleMarykTypeEnum<Values<EmbeddedMarykModel, Properties>>(3u)

    class UnknownMarykTypeEnum(index: UInt, override val name: String): SimpleMarykTypeEnum<Any>(index)

    companion object : IndexedEnumDefinition<SimpleMarykTypeEnum<*>>(
        SimpleMarykTypeEnum::class,
        values = { arrayOf(S1, S2, S3) },
        reservedIndices = listOf(99u),
        reservedNames = listOf("O99"),
        unknownCreator = ::UnknownMarykTypeEnum
    )
}
