package maryk.core.properties.graph

import maryk.core.models.IsDataModel

@Suppress("unused")
/** Defines an element which can be used within a graph */
interface IsPropRefGraphable<in DM: IsDataModel<*>> {
    val graphType: PropRefGraphType
}
