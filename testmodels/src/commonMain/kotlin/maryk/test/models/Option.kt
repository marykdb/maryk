package maryk.test.models

import maryk.core.properties.enum.IndexedEnumDefinition
import maryk.core.properties.enum.IndexedEnumImpl

sealed class Option(
    override val index: UInt
) : IndexedEnumImpl<Option>(index) {
    object V1: Option(1u)
    object V2: Option(2u)
    object V3: Option(3u)
    class UnknownOption(index: UInt, override val name: String): Option(index)

    companion object : IndexedEnumDefinition<Option>(
        enumClass = Option::class,
        values = { arrayOf(V1, V2, V3) },
        reservedIndices = listOf(4u),
        reservedNames = listOf("V4"),
        unknownCreator = ::UnknownOption
    )
}
