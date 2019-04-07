package maryk.core.definitions

import maryk.core.properties.enum.IndexedEnum
import maryk.core.properties.enum.IndexedEnumDefinition
import maryk.core.properties.enum.IsCoreEnum

enum class PrimitiveType(
    override val index: UInt
) : IndexedEnum<PrimitiveType>, IsCoreEnum {
    RootModel(1u), Model(2u), ValueModel(3u), EnumDefinition(4u);

    companion object : IndexedEnumDefinition<PrimitiveType>(
        "PrimitiveType", { PrimitiveType.values() }
    )
}
