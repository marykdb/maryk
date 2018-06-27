package maryk.core.properties.graph

import maryk.core.properties.enum.IndexedEnum
import maryk.core.properties.enum.IndexedEnumDefinition

/** Indexed type of property reference graph elements */
enum class PropRefGraphType(
    override val index: Int
): IndexedEnum<PropRefGraphType> {
    PropRef(0),
    Graph(1);

    companion object: IndexedEnumDefinition<PropRefGraphType>(
        "PropRefGraphType", PropRefGraphType::values
    )
}
