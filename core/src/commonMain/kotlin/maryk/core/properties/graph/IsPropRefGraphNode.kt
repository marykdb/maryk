package maryk.core.properties.graph

import maryk.core.properties.IsPropertyDefinitions

/** Defines an element which can be used within a graph */
interface IsPropRefGraphNode<in P : IsPropertyDefinitions> {
    val index: UInt
    val graphType: PropRefGraphType
}
