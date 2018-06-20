package maryk.core.objects.graph

import maryk.core.properties.enum.IndexedEnum
import maryk.core.properties.enum.IndexedEnumDefinition

/** Indexed type of graph elements */
enum class GraphType(
    override val index: Int
): IndexedEnum<GraphType> {
    PropRef(0),
    Graph(1);

    companion object: IndexedEnumDefinition<GraphType>(
        "GraphType", GraphType::values
    )
}
