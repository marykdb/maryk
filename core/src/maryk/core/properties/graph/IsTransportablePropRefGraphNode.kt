package maryk.core.properties.graph

/**
 * Defines an element which can be used within a graph for transport.
 * In transport references are used instead of definition wrappers
 */
interface IsTransportablePropRefGraphNode {
    val index: UInt
    val graphType: PropRefGraphType
}
