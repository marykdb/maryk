package maryk.core.properties.graph

import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.IsMapDefinition
import maryk.core.properties.definitions.wrapper.IsDefinitionWrapper
import maryk.core.properties.references.IsMapReference
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.references.IsPropertyReferenceForValues
import maryk.core.properties.types.TypedValue
import maryk.core.values.ObjectValues
import maryk.core.values.ValueItems
import maryk.lib.exceptions.ParseException

internal data class GraphMapKey(val reference: IsMapReference<*, *, *, *>, val key: Any)

/** The first opening and final closing bracket delimit the key; brackets inside it are data. */
@Suppress("UNCHECKED_CAST")
internal fun readGraphMapKey(name: String, context: GraphContext?): GraphMapKey {
    val opening = name.indexOf('[')
    if (opening <= 0 || !name.endsWith(']')) throw ParseException("Expected map[key] selection")
    val reference = context?.dataModel?.getPropertyReferenceByName(name.substring(0, opening), context) as? IsMapReference<*, *, *, *>
        ?: throw ParseException("Expected MapReference")
    requireLocalGraphReference(reference)
    val definition = reference.comparablePropertyDefinition as IsMapDefinition<Any, Any, IsPropertyContext>
    return GraphMapKey(reference, definition.keyDefinition.fromString(name.substring(opening + 1, name.lastIndex), context))
}

@Suppress("UNCHECKED_CAST")
internal fun GraphMapItem<*, *>.jsonMapKeyName(context: GraphContext?): String {
    val definition = mapReference.comparablePropertyDefinition as IsMapDefinition<Any, Any, IsPropertyContext>
    return "${mapReference.name}[${definition.keyDefinition.asString(key, context)}]"
}

/** Preserve arbitrary simple key types without narrowing them to Comparable in the raw value slot. */
internal fun graphMapValues(
    reference: IsMapReference<*, *, *, *>,
    key: Any,
    properties: List<TypedValue<PropRefGraphType, IsTransportablePropRefGraphNode>>,
    context: GraphContext?,
): ObjectValues<GraphMapItem<*, *>, GraphMapItem.Companion> = GraphMapItem.Serializer.createValues(context, ValueItems(
    GraphMapItem.mapReference asValueItem reference,
    GraphMapItem.key asValueItem key,
    GraphMapItem.properties asValueItem properties,
))

/** Graph nodes store local indexes; dropping an ancestor would select a different property. */
internal fun requireLocalGraphReference(reference: IsPropertyReference<*, *, *>) {
    if (reference.unwrap().size != 1) throw ParseException("Use nested graph branches for nonlocal property references")
}

internal fun graphPropertyDefinition(value: IsTransportablePropRefGraphNode): IsDefinitionWrapper<*, *, *, *> {
    val reference = value as? IsPropertyReferenceForValues<*, *, *, *>
        ?: throw ParseException("Expected a local graph property reference")
    requireLocalGraphReference(reference)
    return reference.propertyDefinition
}
