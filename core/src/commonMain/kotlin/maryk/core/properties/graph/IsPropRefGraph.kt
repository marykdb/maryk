package maryk.core.properties.graph

import maryk.core.exceptions.TypeException
import maryk.core.models.IsDataModel
import maryk.core.properties.definitions.wrapper.IsDefinitionWrapper
import maryk.core.properties.references.EmbeddedObjectPropertyRef
import maryk.core.properties.references.EmbeddedValuesPropertyRef
import maryk.core.properties.references.IsMapReference
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.references.IsPropertyReferenceForValues
import maryk.core.properties.references.MultiTypePropertyReference
import maryk.core.properties.references.TypedValueReference
import maryk.core.properties.references.MapValueReference

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
        val elements = reference.unwrap()

        var currentNode: IsPropRefGraph<*> = this
        var currentMapKey: GraphMapItem<*, *>? = null

        for ((index, currentReference) in elements.withIndex()) {
            return when (currentReference) {
                is IsMapReference<*, *, *, *> -> {
                    if (index != elements.lastIndex) {
                        when (val node = currentNode.selectNodeOrNull(currentReference.index)) {
                            is PropRefGraph<*, *> -> break // Should not contain a PropRefGraph
                            is GraphMapItem<*, *> -> {
                                currentMapKey = node
                                continue // To next MapValueReference
                            }
                        }
                    }
                    currentNode.contains(currentReference.index)
                }
                is MapValueReference<*, *, *> -> {
                    val matches = currentMapKey != null && currentMapKey.key == currentReference.key
                    if (matches) {
                        @Suppress("UNCHECKED_CAST")
                        currentNode = currentMapKey as IsPropRefGraph<*>
                        currentMapKey = null
                    }
                    matches
                }
                is TypedValueReference<*, *, *> -> {
                    if (index != elements.lastIndex) {
                        continue
                    } else currentNode is TypePropRefGraph<*, *, *>
                }
                is IsPropertyReferenceForValues<*, *, *, *> -> {
                    if (index != elements.lastIndex && (currentReference is EmbeddedValuesPropertyRef<*, *> || currentReference is EmbeddedObjectPropertyRef<*, *, *, *, *> || currentReference is MultiTypePropertyReference<*, *, *, *, *>)) {
                        when (val node = currentNode.selectNodeOrNull(currentReference.index)) {
                            is PropRefGraph<*, *> -> {
                                currentNode = node
                                continue
                            }
                            is TypePropRefGraph<*, *, *> -> {
                                currentNode = node
                                continue
                            }
                            else -> currentNode.contains(currentReference.index)
                        }
                    } else currentNode.contains(currentReference.index)
                }
                else -> false
            }
        }

        return false
    }
}
