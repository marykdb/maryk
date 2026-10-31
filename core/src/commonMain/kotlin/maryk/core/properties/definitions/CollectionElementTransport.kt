package maryk.core.properties.definitions

import maryk.core.models.migration.readsLegacyNanosDateTime
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.contextual.ContextCaptureDefinition
import maryk.core.properties.definitions.contextual.ContextCollectionTransformerDefinition
import maryk.core.properties.definitions.contextual.ContextInjectCollectionOnWriteDefinition
import maryk.core.properties.definitions.contextual.ContextTransformerDefinition
import maryk.core.properties.definitions.contextual.ContextValueTransformDefinition
import maryk.core.properties.definitions.contextual.ContextualCollectionDefinition
import maryk.core.properties.definitions.contextual.ContextualMapDefinition
import maryk.core.properties.definitions.contextual.ContextualNumberDefinition
import maryk.core.properties.definitions.contextual.ContextualPropertyValueDefinition
import maryk.core.properties.definitions.contextual.ContextualSubDefinition
import maryk.core.properties.definitions.contextual.ContextualValueDefinition
import maryk.core.properties.definitions.wrapper.IsDefinitionWrapper
import maryk.core.properties.types.TimePrecision
import maryk.core.protobuf.WireType
import maryk.core.protobuf.WireType.VAR_INT

private data class ResolvedCollectionElement(
    val definition: IsSerializablePropertyDefinition<*, *>,
    val context: IsPropertyContext?,
)

/** Inspect shape only; reads and writes still go through the original wrappers and their hooks. */
@Suppress("UNCHECKED_CAST")
private fun IsSerializablePropertyDefinition<*, *>.resolveCollectionElement(
    context: IsPropertyContext?,
): ResolvedCollectionElement = when (this) {
    is IsDefinitionWrapper<*, *, *, *> -> definition.resolveCollectionElement(context)
    is ContextTransformerDefinition<*, *, *> ->
        (this as ContextTransformerDefinition<Any, IsPropertyContext, IsPropertyContext>)
            .definition.resolveCollectionElement(transformContext(context))
    is ContextCaptureDefinition<*, *> -> definition.resolveCollectionElement(context)
    is ContextValueTransformDefinition<*, *> -> definition.resolveCollectionElement(context)
    is ContextInjectCollectionOnWriteDefinition<*, *, *> -> definition.resolveCollectionElement(context)
    is ContextCollectionTransformerDefinition<*, *, *, *> ->
        (this as ContextCollectionTransformerDefinition<Any, Collection<Any>, IsPropertyContext, IsPropertyContext>)
            .definition.resolveCollectionElement(contextTransformer(context))
    is ContextualValueDefinition<*, *, *, *> ->
        (this as ContextualValueDefinition<IsPropertyContext, IsPropertyContext, Any, IsValueDefinition<Any, IsPropertyContext>>)
            .contextualResolver(context).resolveCollectionElement(contextTransformer(context))
    is ContextualSubDefinition<*, *, *, *> ->
        (this as ContextualSubDefinition<IsPropertyContext, IsPropertyContext, Any, IsSubDefinition<Any, IsPropertyContext>>)
            .contextualResolver(context).resolveCollectionElement(contextTransformer(context))
    is ContextualCollectionDefinition<*> ->
        (this as ContextualCollectionDefinition<IsPropertyContext>)
            .contextualResolver(context).resolveCollectionElement(context)
    is ContextualMapDefinition<*, *, *> ->
        (this as ContextualMapDefinition<Any, Any, IsPropertyContext>)
            .contextualResolver(context).resolveCollectionElement(context)
    is ContextualPropertyValueDefinition<*, *, *> -> {
        val typed = this as ContextualPropertyValueDefinition<IsPropertyContext, IsPropertyContext, Any>
        val definition = typed.contextualResolver(context)
        val innerContext = typed.contextTransformer(context)
        if (definition.isTransportCollection(innerContext)) ResolvedCollectionElement(this, context)
        else definition.resolveCollectionElement(innerContext)
    }
    else -> ResolvedCollectionElement(this, context)
}

/** The wire type of one element, with complete collection-valued elements retaining their envelope. */
@Suppress("UNCHECKED_CAST")
internal fun IsSerializablePropertyDefinition<*, *>.collectionElementWireType(context: IsPropertyContext?): WireType? {
    val resolved = resolveCollectionElement(context)
    return when (val definition = resolved.definition) {
        is DateTimeDefinition ->
            if (definition.precision == TimePrecision.NANOS && resolved.context.readsLegacyNanosDateTime()) VAR_INT
            else definition.wireType
        is ContextualNumberDefinition<*> ->
            (definition as ContextualNumberDefinition<IsPropertyContext>).contextualResolver(resolved.context).wireType
        is IsValueDefinition<*, *> -> definition.wireType
        else -> null
    }
}
