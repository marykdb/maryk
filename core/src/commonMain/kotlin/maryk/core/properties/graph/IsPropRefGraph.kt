package maryk.core.properties.graph

import maryk.core.exceptions.TypeException
import maryk.core.properties.IsPropertyDefinitions
import maryk.core.properties.definitions.wrapper.IsDefinitionWrapper

/** Defines a graph element */
interface IsPropRefGraph<in P : IsPropertyDefinitions> {
    val properties: List<IsPropRefGraphNode<P>>

    /** Select a node by [index] or return null if not exists */
    fun selectNodeOrNull(index: UInt): IsPropRefGraphNode<P>? {
        val propertyIndex = this.properties.binarySearch { property -> property.index.compareTo(index) }
        if (propertyIndex < 0) {
            // Not in select so skip!
            return null
        }
        return this.properties[propertyIndex]
    }

    fun renderPropsAsString() = buildString {
        properties.forEach {
            if (isNotBlank()) append(", ")
            when (it) {
                is IsDefinitionWrapper<*, *, *, *> -> append(it.name)
                is PropRefGraph<*, *, *> -> append(it)
                else -> throw TypeException("Unknown Graphable type")
            }
        }
    }
}
