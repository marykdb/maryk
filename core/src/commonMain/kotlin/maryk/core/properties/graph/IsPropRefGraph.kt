package maryk.core.properties.graph

import maryk.core.exceptions.TypeException
import maryk.core.properties.IsPropertyDefinitions
import maryk.core.properties.definitions.wrapper.IsDefinitionWrapper
import maryk.core.properties.references.EmbeddedObjectPropertyRef
import maryk.core.properties.references.EmbeddedValuesPropertyRef
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.references.IsPropertyReferenceForValues

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

    /** Check if select can match [index] */
    fun contains(index: UInt) =
        0 <= this.properties.binarySearch { property -> property.index.compareTo(index) }

    /** Check if select can match [reference] */
    fun contains(reference: IsPropertyReference<*, *, *>): Boolean {
        val elements = reference.unwrap()

        var referenceIndex = 0
        var currentReference = elements[referenceIndex++]
        var currentSelect: IsPropRefGraph<*> = this

        loop@ while (referenceIndex <= elements.size) {
            return when (currentReference) {
                is IsPropertyReferenceForValues<*, *, *, *> -> {
                    if (referenceIndex < elements.size && currentReference is EmbeddedValuesPropertyRef<*, *, *> || currentReference is EmbeddedObjectPropertyRef<*, *, *, *, *, *>) {
                        when (val node = currentSelect.selectNodeOrNull(currentReference.index)) {
                            is PropRefGraph<*, *, *> -> {
                                currentReference = elements[referenceIndex++]
                                currentSelect = node
                                continue@loop
                            }
                            else -> currentSelect.contains(currentReference.index)
                        }
                    } else currentSelect.contains(currentReference.index)
                }
                else -> false
            }
        }

        return false
    }
}
