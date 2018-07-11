package maryk

import maryk.core.properties.enum.IndexedEnum
import maryk.core.properties.enum.IndexedEnumDefinition

enum class Option(
    override val index: Int
): IndexedEnum<Option> {
    V0(0), V1(1), V2(2);

    companion object: IndexedEnumDefinition<Option>("Option", Option::values)
}
