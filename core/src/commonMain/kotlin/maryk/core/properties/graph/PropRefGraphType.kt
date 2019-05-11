package maryk.core.properties.graph

import maryk.core.properties.enum.IndexedEnumComparable
import maryk.core.properties.enum.IndexedEnumDefinition
import maryk.core.properties.enum.IsCoreEnum
import maryk.core.properties.enum.TypeEnum

/** Indexed type of property reference graph elements */
enum class PropRefGraphType(
    override val index: UInt,
    override val alternativeNames: Set<String>? = null
) : IndexedEnumComparable<PropRefGraphType>, IsCoreEnum, TypeEnum<IsTransportablePropRefGraphNode> {
    PropRef(1u),
    Graph(2u);

    companion object : IndexedEnumDefinition<PropRefGraphType>(
        "PropRefGraphType", PropRefGraphType::values
    )
}
