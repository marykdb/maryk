package maryk.core.properties.graph

import maryk.core.properties.enum.IndexedEnum
import maryk.core.properties.enum.IndexedEnumDefinition

/** Indexed type of property reference graph elements */
enum class PropRefGraphType(
    override val index: UInt
): IndexedEnum<PropRefGraphType> {
    PropRef(1u),
    Graph(2u);

    companion object: IndexedEnumDefinition<PropRefGraphType>(
        "PropRefGraphType", PropRefGraphType::values
    )
}
