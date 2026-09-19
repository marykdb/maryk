package maryk.core.properties.graph

import maryk.core.models.IsDataModel

/** Defines an element which can be used within a graph */
interface IsPropRefGraphNode<in DM : IsDataModel> {
    val index: UInt
    val graphType: PropRefGraphType
}
