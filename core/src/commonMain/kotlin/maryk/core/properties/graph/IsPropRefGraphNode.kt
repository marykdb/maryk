package maryk.core.properties.graph

import maryk.core.properties.IsPropertyDefinitions

@Suppress("unused")
/** Defines an element which can be used within a graph */
interface IsPropRefGraphNode<in P : IsPropertyDefinitions> {
    val index: Int
    val graphType: PropRefGraphType
}
