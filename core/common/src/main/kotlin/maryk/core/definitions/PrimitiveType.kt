package maryk.core.definitions

import maryk.core.properties.enum.IndexedEnum
import maryk.core.properties.enum.IndexedEnumDefinition

enum class PrimitiveType(
    override val index: Int
) : IndexedEnum<PrimitiveType> {
    RootModel(0), Model(1), ValueModel(2), EnumDefinition(3);

    companion object : IndexedEnumDefinition<PrimitiveType>(
        "PrimitiveType", { PrimitiveType.values() }
    )
}
