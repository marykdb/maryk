package maryk

import maryk.core.properties.enum.IndexedEnum
import maryk.core.properties.enum.IndexedEnumDefinition

enum class MarykEnum(
    override val index: Int
): IndexedEnum<MarykEnum> {
    O1(1),
    O2(2),
    O3(3);

    companion object: IndexedEnumDefinition<MarykEnum>(
        "MarykEnum", MarykEnum::values
    )
}
