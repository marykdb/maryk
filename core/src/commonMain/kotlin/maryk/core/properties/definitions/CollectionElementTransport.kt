package maryk.core.properties.definitions

import maryk.core.models.migration.readsLegacyNanosDateTime
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.contextual.ContextualPropertyValueDefinition
import maryk.core.properties.definitions.contextual.ContextualReferenceDefinition
import maryk.core.properties.definitions.contextual.ContextualPropertyReferenceDefinition
import maryk.core.properties.definitions.wrapper.IsDefinitionWrapper
import maryk.core.properties.types.TimePrecision
import maryk.core.protobuf.WireType
import maryk.core.protobuf.WireType.VAR_INT
import maryk.core.protobuf.WireType.LENGTH_DELIMITED

/** Inspect element shape without bypassing contextual hooks or collection envelopes. */
@Suppress("UNCHECKED_CAST")
internal fun IsSerializablePropertyDefinition<*, *>.collectionElementWireType(
    context: IsPropertyContext?,
): WireType? = when (this) {
    is StringDefinition -> wireType
    is NumberDefinition<*> -> wireType
    is IsDefinitionWrapper<*, *, *, *> -> definition.collectionElementWireType(context)
    is ContextualReferenceDefinition<*>, is ContextualPropertyReferenceDefinition<*> -> LENGTH_DELIMITED
    is ContextualPropertyValueDefinition<*, *, *> -> {
        val typed = this as ContextualPropertyValueDefinition<IsPropertyContext, IsPropertyContext, Any>
        val definition = typed.contextualResolver(context)
        val innerContext = typed.contextTransformer(context)
        if (definition.isTransportCollection(innerContext)) wireType
        else definition.collectionElementWireType(innerContext)
    }
    is DateTimeDefinition ->
        if (precision == TimePrecision.NANOS && context.readsLegacyNanosDateTime()) VAR_INT else wireType
    is IsContextualEncodable<*, *> -> contextualCollectionElementWireType(context)
    is IsValueDefinition<*, *> -> wireType
    else -> null
}
