package maryk.core.properties.definitions

import maryk.core.properties.definitions.wrapper.IsDefinitionWrapper
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.contextual.ContextualValueDefinition
import maryk.core.properties.definitions.contextual.ContextualSubDefinition
import maryk.core.properties.types.Bytes
import maryk.core.properties.types.TypedValue
import maryk.core.properties.types.numeric.Float32
import maryk.core.properties.types.numeric.Float64
import maryk.core.values.AbstractValues
import maryk.lib.exceptions.ParseException

/** Collection identities follow numeric equality; ordinary value payloads retain raw NaN bits. */
@Suppress("UNCHECKED_CAST")
internal fun <T : Any> IsStorageBytesEncodable<T>.writeCollectionQualifierBytes(value: T, writer: (Byte) -> Unit) {
    val definition = if (this is IsDefinitionWrapper<*, *, *, *>) definition else this
    val canonical = when {
        definition is NumberDefinition<*> && definition.type == Float32 && (value as Float).isNaN() -> Float.NaN
        definition is NumberDefinition<*> && definition.type == Float64 && (value as Double).isNaN() -> Double.NaN
        else -> value
    }
    writeStorageBytes(canonical as T, writer)
}

internal fun <T : Any> IsStorageBytesEncodable<T>.toCollectionQualifierBytes(value: T): ByteArray =
    ByteArray(calculateStorageByteLength(value)).also { bytes ->
        var offset = 0
        writeCollectionQualifierBytes(value) { bytes[offset++] = it }
    }

@Suppress("UNCHECKED_CAST")
internal fun IsPropertyDefinition<*>.canonicalCollectionQualifierBytes(bytes: ByteArray): ByteArray {
    val definition = if (this is IsDefinitionWrapper<*, *, *, *>) definition else this
    if (definition !is NumberDefinition<*> || definition.type != Float32 && definition.type != Float64) return bytes
    val codec = this as IsStorageBytesEncodable<Any>
    var offset = 0
    return codec.toCollectionQualifierBytes(codec.readStorageBytes(bytes.size) { bytes[offset++] })
}

private fun <T : Any> IsStorageBytesEncodable<T>.requireDistinctQualifiers(values: Iterable<T>) {
    val identities = mutableSetOf<Bytes>()
    for (value in values) {
        val encoded = try {
            toCollectionQualifierBytes(value)
        } catch (error: ClassCastException) {
            throw ParseException("Collection member does not match its storage definition", error)
        }
        if (!identities.add(Bytes(encoded))) {
            throw ParseException("Distinct collection members encode to the same storage identity: $value")
        }
    }
}

/** Preflight the entire supplied value before a writer emits collection sizes or nested cells. */
@Suppress("UNCHECKED_CAST")
internal fun IsPropertyDefinition<*>.validateCollectionStorageIdentities(value: Any, context: IsPropertyContext? = null) {
    if (this is IsDefinitionWrapper<*, *, *, *>) {
        definition.validateCollectionStorageIdentities(value, context)
        return
    }
    when {
        this is IsMapDefinition<*, *, *> && value is Map<*, *> -> {
            val definition = this as IsMapDefinition<Any, Any, *>
            val map = value as Map<Any, Any>
            definition.keyDefinition.collectionStorageCodec(context)?.requireDistinctQualifiers(map.keys)
            map.values.forEach { definition.valueDefinition.validateCollectionStorageIdentities(it, context) }
        }
        this is IsSetDefinition<*, *> && value is Set<*> ->
            valueDefinition.collectionStorageCodec(context)?.requireDistinctQualifiers(value as Set<Any>)
        this is IsListDefinition<*, *> && value is List<*> ->
            value.forEach { if (it != null) valueDefinition.validateCollectionStorageIdentities(it, context) }
        this is IsMultiTypeDefinition<*, *, *> && value is TypedValue<*, *> ->
            definition(value.type.index)?.validateCollectionStorageIdentities(value.value, context)
        this is IsEmbeddedValuesDefinition<*, *> && value is AbstractValues<*, *> ->
            value.forEach { (index, item) -> value.dataModel[index]?.definition?.validateCollectionStorageIdentities(item, context) }
    }
}

@Suppress("UNCHECKED_CAST")
private fun IsPropertyDefinition<*>.collectionStorageCodec(context: IsPropertyContext?): IsStorageBytesEncodable<Any>? = when (this) {
    is IsDefinitionWrapper<*, *, *, *> -> definition.collectionStorageCodec(context)
    // Integer encodings are injective, and their ordinary validation owns invalid native-type errors.
    is NumberDefinition<*> -> if (type == Float32 || type == Float64) this as IsStorageBytesEncodable<Any> else null
    is IsStorageBytesEncodable<*> -> this as IsStorageBytesEncodable<Any>
    is ContextualValueDefinition<*, *, *, *> -> if (context == null) null else
        (this as ContextualValueDefinition<IsPropertyContext, IsPropertyContext, Any, IsValueDefinition<Any, IsPropertyContext>>)
            .contextualResolver(context).collectionStorageCodec(contextTransformer(context))
    is ContextualSubDefinition<*, *, *, *> -> if (context == null) null else
        (this as ContextualSubDefinition<IsPropertyContext, IsPropertyContext, Any, IsSubDefinition<Any, IsPropertyContext>>)
            .contextualResolver(context).collectionStorageCodec(contextTransformer(context))
    // Query alternatives can be whole collections/envelopes with no persisted member qualifier.
    else -> null
}
