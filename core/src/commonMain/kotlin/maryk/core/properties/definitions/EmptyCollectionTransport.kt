package maryk.core.properties.definitions

import maryk.core.properties.IsPropertyContext
import maryk.core.models.migration.transportWireType
import maryk.core.properties.definitions.contextual.ContextCollectionTransformerDefinition
import maryk.core.properties.definitions.contextual.ContextualCollectionDefinition
import maryk.core.properties.definitions.contextual.ContextualMapDefinition
import maryk.core.properties.definitions.contextual.ContextualSubDefinition
import maryk.core.properties.definitions.contextual.ContextInjectCollectionOnWriteDefinition
import maryk.core.properties.definitions.wrapper.IsDefinitionWrapper
import maryk.core.protobuf.WireType.BIT_32
import maryk.core.protobuf.WireType.BIT_64
import maryk.core.protobuf.WireType.VAR_INT

internal const val EMPTY_COLLECTION_TAG_OFFSET = 32768u
internal const val EMPTY_MULTI_TYPE_COLLECTION_TAG_OFFSET = 65536u

private data class ResolvedCollection(
    val definition: IsSerializablePropertyDefinition<*, *>,
    val context: IsPropertyContext?,
    val injectedOnWrite: Boolean = false,
)

@Suppress("UNCHECKED_CAST")
private fun IsSerializablePropertyDefinition<*, *>.resolveTransportCollection(context: IsPropertyContext?): ResolvedCollection? = when (this) {
    is IsCollectionDefinition<*, *, *, *>, is IsMapDefinition<*, *, *> -> ResolvedCollection(this, context)
    is IsDefinitionWrapper<*, *, *, *> -> definition.resolveTransportCollection(context)
    is ContextInjectCollectionOnWriteDefinition<*, *, *> ->
        definition.resolveTransportCollection(context)?.copy(injectedOnWrite = true)
    is ContextualCollectionDefinition<*> ->
        (this as ContextualCollectionDefinition<IsPropertyContext>).contextualResolver(context).resolveTransportCollection(context)
    is ContextualMapDefinition<*, *, *> ->
        (this as ContextualMapDefinition<Any, Any, IsPropertyContext>).contextualResolver(context).resolveTransportCollection(context)
    is ContextCollectionTransformerDefinition<*, *, *, *> ->
        (this as ContextCollectionTransformerDefinition<Any, Collection<Any>, IsPropertyContext, IsPropertyContext>)
            .definition.resolveTransportCollection(contextTransformer(context))
    is ContextualSubDefinition<*, *, *, *> ->
        (this as ContextualSubDefinition<IsPropertyContext, IsPropertyContext, Any, IsSubDefinition<Any, IsPropertyContext>>)
            .contextualResolver(context).resolveTransportCollection(contextTransformer(context))
    else -> null
}

internal fun IsSerializablePropertyDefinition<*, *>.isTransportCollection(context: IsPropertyContext?): Boolean =
    resolveTransportCollection(context) != null

internal fun IsSerializablePropertyDefinition<*, *>.needsEmptyCollectionMarker(value: Any, context: IsPropertyContext?): Boolean {
    if ((value !is Collection<*> || value.isNotEmpty()) && (value !is Map<*, *> || value.isNotEmpty())) return false
    val resolved = resolveTransportCollection(context) ?: return false
    // Let the injector decide what to write; the supplied placeholder may be empty.
    if (resolved.injectedOnWrite) return false
    return when (val definition = resolved.definition) {
        is IsMapDefinition<*, *, *> -> true
        is IsCollectionDefinition<*, *, *, *> -> when ((definition.valueDefinition as? IsValueDefinition<*, *>)?.transportWireType(resolved.context)) {
            VAR_INT, BIT_32, BIT_64 -> false
            else -> true
        }
        else -> false
    }
}

@Suppress("UNCHECKED_CAST")
internal fun IsSerializablePropertyDefinition<*, *>.emptyTransportCollection(context: IsPropertyContext?): Any? {
    val resolved = resolveTransportCollection(context) ?: return null
    return when (val definition = resolved.definition) {
        is IsCollectionDefinition<*, *, *, *> ->
            (definition as IsCollectionDefinition<Any, Collection<Any>, IsPropertyContext, IsSubDefinition<Any, IsPropertyContext>>)
                .newMutableCollection(resolved.context)
        is IsMapDefinition<*, *, *> -> mutableMapOf<Any, Any>()
        else -> null
    }
}
