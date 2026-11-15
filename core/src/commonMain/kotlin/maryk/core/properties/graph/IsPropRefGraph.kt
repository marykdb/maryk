package maryk.core.properties.graph

import maryk.core.exceptions.TypeException
import maryk.core.models.IsDataModel
import maryk.core.properties.definitions.wrapper.IsDefinitionWrapper
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.references.IsPropertyReferenceForValues
import maryk.core.properties.references.IsPropertyReferenceWithParent
import maryk.core.properties.references.ValueWithFixedBytesPropertyReference
import maryk.core.properties.references.ValueWithFlexBytesPropertyReference

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
        // Built-in root scalars have a one-element path. Custom references can
        // override unwrap(), so retain the full matcher for every other shape.
        if ((reference::class == ValueWithFixedBytesPropertyReference::class ||
                reference::class == ValueWithFlexBytesPropertyReference::class) &&
            (reference as IsPropertyReferenceWithParent<*, *, *, *>).parentReference == null
        ) {
            val index = (reference as IsPropertyReferenceForValues<*, *, *, *>).index
            return properties.any { it.index == index }
        }
        return matchesSelection(properties, reference.unwrap())
    }
}
