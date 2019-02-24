package maryk.test.models

import maryk.core.properties.enum.IndexedEnum
import maryk.core.properties.enum.IndexedEnumDefinition

enum class MarykEnum(
    override val index: UInt
) : IndexedEnum<MarykEnum> {
    O1(1u),
    O2(2u),
    O3(3u);

    companion object : IndexedEnumDefinition<MarykEnum>(
        "MarykEnum", MarykEnum::values
    )
}
