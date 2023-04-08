package maryk.core.definitions

import maryk.core.properties.enum.IndexedEnumComparable
import maryk.core.properties.enum.IndexedEnumDefinition
import maryk.core.properties.enum.IsCoreEnum
import maryk.core.properties.enum.TypeEnum

enum class PrimitiveType(
    override val index: UInt,
    override val alternativeNames: Set<String>? = null
) : IndexedEnumComparable<PrimitiveType>, TypeEnum<MarykPrimitive>, IsCoreEnum {
    RootModel(1u), Model(2u), ValueModel(3u), EnumDefinition(4u), TypeDefinition(5u), ObjectModel(6u);

    companion object : IndexedEnumDefinition<PrimitiveType>(
        "PrimitiveType", ::values
    )
}
