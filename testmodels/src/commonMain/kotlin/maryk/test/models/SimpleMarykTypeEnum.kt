package maryk.test.models

import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.EmbeddedValuesDefinition
import maryk.core.properties.definitions.IsUsableInMultiType
import maryk.core.properties.definitions.NumberDefinition
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.enum.IndexedEnumImpl
import maryk.core.properties.enum.MultiTypeEnum
import maryk.core.properties.enum.MultiTypeEnumDefinition
import maryk.core.properties.types.numeric.SInt16
import maryk.core.values.Values

sealed class SimpleMarykTypeEnum<T: Any>(
    index: UInt,
    override val definition: IsUsableInMultiType<T, IsPropertyContext>?,
    alternativeNames: Set<String>? = null
) : IndexedEnumImpl<SimpleMarykTypeEnum<*>>(index, alternativeNames), MultiTypeEnum<T> {
    object S1: SimpleMarykTypeEnum<String>(1u, StringDefinition(regEx = "[^&]+"), setOf("Type1"))
    object S2: SimpleMarykTypeEnum<Short>(2u, NumberDefinition(type = SInt16))
    object S3: SimpleMarykTypeEnum<Values<EmbeddedMarykModel>>(3u,
        EmbeddedValuesDefinition(dataModel = { EmbeddedMarykModel.Model })
    )

    class UnknownMarykTypeEnum(index: UInt, override val name: String): SimpleMarykTypeEnum<Any>(index, null)

    companion object : MultiTypeEnumDefinition<SimpleMarykTypeEnum<out Any>>(
        SimpleMarykTypeEnum::class,
        values = { arrayOf(S1, S2, S3) },
        reservedIndices = listOf(99u),
        reservedNames = listOf("O99"),
        unknownCreator = ::UnknownMarykTypeEnum
    )
}
