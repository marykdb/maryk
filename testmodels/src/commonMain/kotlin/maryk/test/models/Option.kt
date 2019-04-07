package maryk.test.models

import maryk.core.properties.enum.IndexedEnum
import maryk.core.properties.enum.IndexedEnumDefinition

enum class Option(
    override val index: UInt
) : IndexedEnum<Option> {
    V1(1u), V2(2u), V3(3u);

    companion object : IndexedEnumDefinition<Option>("Option", Option::values, reserved = listOf(4u), reservedNames = listOf("V4"))
}
