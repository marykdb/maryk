package maryk.core.properties.graph

import maryk.core.properties.IsPropertyDefinitions

/** Defines a graph element */
interface IsPropRefGraph<in P: IsPropertyDefinitions> {
    val properties: List<IsPropRefGraphNode<P>>

    /** Select a node by [index] or return null if not exists */
    fun selectNodeOrNull(index: Int): IsPropRefGraphNode<P>? {
        val propertyIndex = this.properties.binarySearch { property -> property.index.compareTo(index) }
        if (propertyIndex < 0) {
            // Not in select so skip!
            return null
        }
        return this.properties[propertyIndex]
    }
}
