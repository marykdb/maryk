package maryk.core.properties.graph

@Suppress("unused")
/** Defines an element which can be used within a graph */
interface IsPropRefGraphable<in DO> {
    val graphType: PropRefGraphType
}
