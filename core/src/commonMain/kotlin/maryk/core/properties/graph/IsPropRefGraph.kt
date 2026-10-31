package maryk.core.properties.graph

import maryk.core.exceptions.TypeException
import maryk.core.models.IsDataModel
import maryk.core.properties.definitions.wrapper.IsDefinitionWrapper
import maryk.core.properties.references.IsPropertyReference

/** Defines a graph element */
interface IsPropRefGraph<in DM : IsDataModel> {
    val properties: List<IsPropRefGraphNode<DM>>

    /** Select a node by [index] or return null if not exists */
    fun selectNodeOrNull(index: UInt): IsPropRefGraphNode<DM>? {
        val propertyIndex = this.properties.binarySearch { property -> property.index compareTo index }
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
                is PropRefGraph<*, *> -> append(it)
                is TypePropRefGraph<*, *, *> -> append(it)
                else -> throw TypeException("Unknown Graphable type")
            }
        }
    }

    /** Check if select can match [index] */
    fun contains(index: UInt) =
        0 <= this.properties.binarySearch { property -> property.index compareTo index }

    /** Check if select can match [reference] */
    fun contains(reference: IsPropertyReference<*, *, *>): Boolean {
        return matchesSelection(properties, reference.unwrap())
    }
}
