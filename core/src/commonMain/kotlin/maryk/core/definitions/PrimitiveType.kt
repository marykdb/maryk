package maryk.core.definitions

import maryk.core.properties.enum.IndexedEnum
import maryk.core.properties.enum.IndexedEnumDefinition

enum class PrimitiveType(
    override val index: UInt
) : IndexedEnum<PrimitiveType> {
    RootModel(1u), Model(2u), ValueModel(3u), EnumDefinition(4u);

    companion object : IndexedEnumDefinition<PrimitiveType>(
        "PrimitiveType", { PrimitiveType.values() }
    )
}
