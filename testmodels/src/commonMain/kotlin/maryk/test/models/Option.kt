package maryk.test.models

import maryk.core.properties.enum.IndexedEnumComparable
import maryk.core.properties.enum.IndexedEnumDefinition

enum class Option(
    override val index: UInt
) : IndexedEnumComparable<Option> {
    V1(1u), V2(2u), V3(3u);

    companion object : IndexedEnumDefinition<Option>(
        enumClass = Option::class,
        values = { arrayOf(V1, V2, V3) },
        reservedIndices = listOf(4u),
        reservedNames = listOf("V4")
    )
}
