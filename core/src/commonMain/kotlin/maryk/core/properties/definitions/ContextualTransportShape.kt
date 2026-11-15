package maryk.core.properties.definitions

import maryk.core.properties.IsPropertyContext
import maryk.core.protobuf.WireType
import maryk.core.protobuf.WireType.LENGTH_DELIMITED
import maryk.core.properties.definitions.contextual.ContextCollectionTransformerDefinition
import maryk.core.properties.definitions.contextual.ContextualCollectionDefinition
import maryk.core.properties.definitions.contextual.ContextualMapDefinition
import maryk.core.properties.definitions.contextual.ContextualSubDefinition
import maryk.core.properties.definitions.contextual.ContextInjectCollectionOnWriteDefinition
import maryk.core.properties.definitions.contextual.ContextCaptureDefinition
import maryk.core.properties.definitions.contextual.ContextTransformerDefinition
import maryk.core.properties.definitions.contextual.ContextValueTransformDefinition
import maryk.core.properties.definitions.contextual.ContextualNumberDefinition
import maryk.core.properties.definitions.contextual.ContextualValueDefinition

// Keep contextual implementation dependencies out of concrete transport-shape checks.
@Suppress("UNCHECKED_CAST")
internal fun IsContextualEncodable<*, *>.resolveContextualTransportCollection(context: IsPropertyContext?): ResolvedCollection? = when (this) {
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

@Suppress("UNCHECKED_CAST")
internal fun IsContextualEncodable<*, *>.contextualCollectionElementWireType(
    context: IsPropertyContext?,
): WireType? = when (this) {
    is ContextTransformerDefinition<*, *, *> ->
        (this as ContextTransformerDefinition<Any, IsPropertyContext, IsPropertyContext>)
            .definition.collectionElementWireType(transformContext(context))
    is ContextCaptureDefinition<*, *> -> definition.collectionElementWireType(context)
    is ContextValueTransformDefinition<*, *> -> definition.collectionElementWireType(context)
    is ContextInjectCollectionOnWriteDefinition<*, *, *> -> definition.collectionElementWireType(context)
    is ContextCollectionTransformerDefinition<*, *, *, *> ->
        (this as ContextCollectionTransformerDefinition<Any, Collection<Any>, IsPropertyContext, IsPropertyContext>)
            .definition.collectionElementWireType(contextTransformer(context))
    is ContextualValueDefinition<*, *, *, *> ->
        (this as ContextualValueDefinition<IsPropertyContext, IsPropertyContext, Any, IsValueDefinition<Any, IsPropertyContext>>)
            .contextualResolver(context).collectionElementWireType(contextTransformer(context))
    is ContextualSubDefinition<*, *, *, *> ->
        (this as ContextualSubDefinition<IsPropertyContext, IsPropertyContext, Any, IsSubDefinition<Any, IsPropertyContext>>)
            .contextualResolver(context).collectionElementWireType(contextTransformer(context))
    is ContextualCollectionDefinition<*> ->
        (this as ContextualCollectionDefinition<IsPropertyContext>)
            .contextualResolver(context).collectionElementWireType(context)
    is ContextualMapDefinition<*, *, *> ->
        (this as ContextualMapDefinition<Any, Any, IsPropertyContext>)
            .contextualResolver(context).collectionElementWireType(context)
    is ContextualNumberDefinition<*> ->
        (this as ContextualNumberDefinition<IsPropertyContext>).contextualResolver(context).wireType
    is IsValueDefinition<*, *> -> wireType
    else -> null
}

@Suppress("UNCHECKED_CAST")
internal fun IsContextualEncodable<*, *>.acceptsOtherContextualProtoBufWireType(
    wireType: WireType,
    context: IsPropertyContext?
): Boolean = when (this) {
    is ContextTransformerDefinition<*, *, *> ->
        (this as ContextTransformerDefinition<Any, IsPropertyContext, IsPropertyContext>)
            .definition.acceptsProtoBufWireType(wireType, transformContext(context))
    is ContextValueTransformDefinition<*, *> -> definition.acceptsProtoBufWireType(wireType, context)
    is ContextCaptureDefinition<*, *> -> definition.acceptsProtoBufWireType(wireType, context)
    is ContextInjectCollectionOnWriteDefinition<*, *, *> -> definition.acceptsProtoBufWireType(wireType, context)
    is ContextualValueDefinition<*, *, *, *> ->
        (this as ContextualValueDefinition<IsPropertyContext, IsPropertyContext, Any, IsValueDefinition<Any, IsPropertyContext>>)
            .contextualResolver(context)
            .acceptsProtoBufWireType(wireType, this.contextTransformer(context))
    is ContextualNumberDefinition<*> ->
        (this as ContextualNumberDefinition<IsPropertyContext>)
            .contextualResolver(context)
            .wireType == wireType
    is ContextualSubDefinition<*, *, *, *> ->
        (this as ContextualSubDefinition<IsPropertyContext, IsPropertyContext, Any, IsSubDefinition<Any, IsPropertyContext>>)
            .contextualResolver(context)
            .acceptsProtoBufWireType(wireType, this.contextTransformer(context))
    is ContextualCollectionDefinition<*> ->
        wireType == LENGTH_DELIMITED ||
            (this as ContextualCollectionDefinition<IsPropertyContext>)
                .contextualResolver(context)
                .acceptsProtoBufWireType(wireType, context)
    is ContextualMapDefinition<*, *, *> ->
        wireType == LENGTH_DELIMITED ||
            (this as ContextualMapDefinition<Any, Any, IsPropertyContext>)
                .contextualResolver(context)
                .acceptsProtoBufWireType(wireType, context)
    is ContextCollectionTransformerDefinition<*, *, *, *> ->
        (this as ContextCollectionTransformerDefinition<Any, Collection<Any>, IsPropertyContext, IsPropertyContext>)
            .definition
            .acceptsProtoBufWireType(wireType, this.contextTransformer(context))
    is IsValueDefinition<*, *> -> wireType == this.wireType
    else -> wireType == LENGTH_DELIMITED
}
