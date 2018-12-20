package maryk.core.properties.graph

import maryk.core.properties.PropertyDefinitions

@Suppress("unused")
/** Defines an element which can be used within a graph */
interface IsPropRefGraphable<in P: PropertyDefinitions> {
    val graphType: PropRefGraphType
}
