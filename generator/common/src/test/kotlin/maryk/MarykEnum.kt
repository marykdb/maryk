package maryk

import maryk.core.properties.types.IndexedEnum
import maryk.core.properties.types.IndexedEnumDefinition

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
