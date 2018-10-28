package maryk.core.definitions

import maryk.core.properties.enum.IndexedEnum
import maryk.core.properties.enum.IndexedEnumDefinition

enum class PrimitiveType(
    override val index: Int
) : IndexedEnum<PrimitiveType> {
    RootModel(1), Model(2), ValueModel(3), EnumDefinition(4);

    companion object : IndexedEnumDefinition<PrimitiveType>(
        "PrimitiveType", { PrimitiveType.values() }
    )
}
