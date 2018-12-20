package maryk.core.properties.graph

import maryk.core.properties.IsPropertyDefinitions

@Suppress("unused")
/** Defines an element which can be used within a graph */
interface IsPropRefGraphable<in P: IsPropertyDefinitions> {
    val graphType: PropRefGraphType
}
