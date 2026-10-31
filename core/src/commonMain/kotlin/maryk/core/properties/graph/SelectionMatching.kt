package maryk.core.properties.graph

import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.IsMapDefinition
import maryk.core.properties.definitions.IsMultiTypeDefinition
import maryk.core.properties.definitions.toCollectionQualifierBytes
import maryk.core.properties.definitions.wrapper.IsDefinitionWrapper
import maryk.core.properties.enum.TypeEnum
import maryk.core.properties.references.CanContainMapItemReference
import maryk.core.properties.references.CanHaveComplexChildReference
import maryk.core.properties.references.IsMapReference
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.references.IsPropertyReferenceForValues
import maryk.core.properties.references.MapValueReference
import maryk.core.properties.references.TypedValueReference
import maryk.core.properties.types.TypedValue
import maryk.core.properties.types.invoke
import maryk.core.values.MutableValueItems
import maryk.core.values.Values

/** Equal map keys use the same identity as stored qualifiers, including normalized scalar keys. */
@Suppress("UNCHECKED_CAST")
internal fun GraphMapItem<*, *>.matchesKey(other: Any): Boolean {
    val codec = (mapReference.comparablePropertyDefinition as IsMapDefinition<Any, Any, IsPropertyContext>).keyDefinition
    return codec.toCollectionQualifierBytes(key).contentEquals(codec.toCollectionQualifierBytes(other))
}

internal fun matchesSelection(
    nodes: List<IsPropRefGraphNode<*>>,
    references: List<IsPropertyReference<*, *, *>>,
    position: Int = 0,
    wholeValue: Boolean = false,
): Boolean {
    val reference = references.getOrNull(position) as? IsPropertyReferenceForValues<*, *, *, *> ?: return false
    return nodes.any { node ->
        if (node.index != reference.index) false
        else if (node is IsDefinitionWrapper<*, *, *, *>) true
        else if (position == references.lastIndex) !wholeValue
        else when (node) {
            is PropRefGraph<*, *> -> matchesSelection(node.properties, references, position + 1, wholeValue)
            is GraphMapItem<*, *> -> {
                val item = references.getOrNull(position + 1) as? MapValueReference<*, *, *>
                reference is IsMapReference<*, *, *, *> && item != null && node.matchesKey(item.key) &&
                    (node.properties.isEmpty() ||
                        if (position + 1 == references.lastIndex) !wholeValue
                        else matchesSelection(node.properties, references, position + 2, wholeValue))
            }
            is TypePropRefGraph<*, *, *> -> {
                val typed = references.getOrNull(position + 1) as? TypedValueReference<*, *, *>
                typed != null && typed.type.index == node.type.index &&
                    (if (position + 1 == references.lastIndex) !wholeValue
                        else matchesSelection(node.properties, references, position + 2, wholeValue))
            }
            else -> false
        }
    }
}

/** Resolve a field reference without assuming that only one graph branch has its index. */
internal fun IsPropRefGraphNode<*>.selectionReference(): IsPropertyReference<*, *, *> = when (this) {
    is IsDefinitionWrapper<*, *, *, *> -> ref()
    is PropRefGraph<*, *> -> parent.ref()
    is GraphMapItem<*, *> -> mapReference
    is TypePropRefGraph<*, *, *> -> parent.ref()
    else -> error("Unsupported graph node $graphType")
}

/** Project snapshots and replacement operands through the same complete reference paths. */
@Suppress("UNCHECKED_CAST")
internal fun projectSelectionValue(
    value: Any,
    reference: IsPropertyReference<*, *, *>,
    select: IsPropRefGraph<*>,
): Any {
    if (matchesSelection(select.properties, reference.unwrap(), wholeValue = true)) return value
    return when (value) {
        is Values<*> -> {
            val items = MutableValueItems()
            for ((index, item) in value.values) {
                val definition = value.dataModel[index] ?: continue
                val child = definition.ref(reference)
                if (select.contains(child)) items[index] = projectSelectionValue(item, child, select)
            }
            Values(value.dataModel, items, value.context)
        }
        is Map<*, *> -> {
            val definition = reference.comparablePropertyDefinition as IsMapDefinition<Any, Any, IsPropertyContext>
            val result = linkedMapOf<Any, Any>()
            for ((key, item) in value) {
                if (key == null || item == null) continue
                val child = definition.valueRef(key, reference as CanContainMapItemReference<*, *, *>)
                if (select.contains(child)) result[key] = projectSelectionValue(item, child, select)
            }
            result
        }
        is TypedValue<*, *> -> {
            val definition = reference.comparablePropertyDefinition as IsMultiTypeDefinition<TypeEnum<Any>, Any, IsPropertyContext>
            val child = TypedValueReference(value.type, definition, reference as CanHaveComplexChildReference<*, *, *, *>)
            value.type(projectSelectionValue(value.value, child, select))
        }
        else -> value
    }
}
