package maryk.test.models

import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.EmbeddedObjectDefinition
import maryk.core.properties.definitions.IsUsableInMultiType
import maryk.core.properties.definitions.NumberDefinition
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.enum.IndexedEnumImpl
import maryk.core.properties.enum.MultiTypeEnum
import maryk.core.properties.enum.MultiTypeEnumDefinition
import maryk.core.properties.types.numeric.SInt16

sealed class SimpleMarykTypeEnumWithObject<T: Any>(
    index: UInt,
    override val definition: IsUsableInMultiType<T, IsPropertyContext>?,
    alternativeNames: Set<String>? = null
) : IndexedEnumImpl<SimpleMarykTypeEnumWithObject<*>>(index, alternativeNames), MultiTypeEnum<T> {
    object S1: SimpleMarykTypeEnumWithObject<String>(1u, StringDefinition(regEx = "[^&]+"), setOf("Type1"))
    object S2: SimpleMarykTypeEnumWithObject<Short>(2u, NumberDefinition(type = SInt16))
    object S3: SimpleMarykTypeEnumWithObject<EmbeddedMarykObject>(3u,
        EmbeddedObjectDefinition(dataModel = { EmbeddedMarykObject.Model })
    )

    class UnknownMarykTypeEnum(index: UInt, override val name: String): SimpleMarykTypeEnumWithObject<Any>(index, null)

    companion object : MultiTypeEnumDefinition<SimpleMarykTypeEnumWithObject<out Any>>(
        SimpleMarykTypeEnumWithObject::class,
        values = { arrayOf(S1, S2, S3) },
        reservedIndices = listOf(99u),
        reservedNames = listOf("O99"),
        unknownCreator = ::UnknownMarykTypeEnum
    )
}
