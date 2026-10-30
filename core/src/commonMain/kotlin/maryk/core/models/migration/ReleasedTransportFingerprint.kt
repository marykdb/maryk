package maryk.core.models.migration

import maryk.core.extensions.bytes.initDouble
import maryk.core.extensions.bytes.initFloat
import maryk.core.extensions.bytes.writeBytes
import maryk.core.extensions.bytes.writeVarBytes
import maryk.core.extensions.bytes.writeVarIntWithExtraInfo
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.DateTimeDefinition
import maryk.core.properties.definitions.IsFixedStorageBytesEncodable
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.definitions.IsStorageBytesEncodable
import maryk.core.properties.definitions.IsValueDefinition
import maryk.core.properties.definitions.NumberDefinition
import maryk.core.properties.definitions.ValueObjectDefinition
import maryk.core.properties.definitions.wrapper.IsDefinitionWrapper
import maryk.core.properties.graph.GraphContext
import maryk.core.properties.references.AnyPropertyReference
import maryk.core.properties.references.IsPropertyReferenceWithParent
import maryk.core.properties.references.MapKeyReference
import maryk.core.properties.references.MapValueReference
import maryk.core.properties.references.ListItemReference
import maryk.core.properties.references.SetItemReference
import maryk.core.properties.references.TypedValueReference
import maryk.core.properties.references.ReferenceType.TYPE
import maryk.core.properties.types.TimePrecision
import maryk.core.properties.types.numeric.Float32
import maryk.core.properties.types.numeric.Float64
import maryk.core.protobuf.WireType
import maryk.core.protobuf.WriteCache
import maryk.core.query.RequestContext

/** A released request encoding cannot establish the identity of this logical request. */
class ReleasedTransportFingerprintException(message: String) : Exception(message)

/**
 * Reproduce released request bytes only to compare retained receipt fingerprints.
 * Use a fresh write cache inside this scope; these bytes must never become new requests or storage values.
 */
fun <T> RequestContext.writeReleasedTransportForFingerprint(write: () -> T): T {
    val previous = releasedTransportForFingerprint
    releasedTransportForFingerprint = true
    return try { write() } finally { releasedTransportForFingerprint = previous }
}

internal fun IsPropertyContext?.writesReleasedTransportForFingerprint(): Boolean =
    when (this) {
        is RequestContext -> releasedTransportForFingerprint
        is GraphContext -> releasedTransportForFingerprint
        else -> false
    }

internal fun IsValueDefinition<*, *>.transportWireType(context: IsPropertyContext?): WireType =
    if (context.writesReleasedTransportForFingerprint() && this is DateTimeDefinition && precision == TimePrecision.NANOS) {
        WireType.VAR_INT
    } else wireType

/** Exact reverse of the released floating storage transition, for receipt identity and old index prefixes only. */
fun IsPropertyDefinition<*>.convertToReleasedFloatingPointBytes(bytes: ByteArray): ByteArray {
    val definition = if (this is IsDefinitionWrapper<*, *, *, *>) definition else this
    return when (definition) {
        is NumberDefinition<*> -> {
            if (definition.type != Float32 && definition.type != Float64) return bytes
            val source = if (definition.reversedStorage == true) ByteArray(bytes.size) { bytes[it].toInt().inv().toByte() } else bytes
            var offset = 0
            val result = mutableListOf<Byte>()
            when (definition.type) {
                Float32 -> {
                    require(bytes.size == 4) { "Invalid current Float32 width" }
                    val bits = initFloat { source[offset++] }.toRawBits()
                    ((bits xor ((bits shr 31) or Int.MIN_VALUE)) + 1).writeBytes(result::add)
                }
                else -> {
                    require(bytes.size == 8) { "Invalid current Float64 width" }
                    val bits = initDouble { source[offset++] }.toRawBits()
                    ((bits xor ((bits shr 63) or Long.MIN_VALUE)) + 1).writeBytes(result::add)
                }
            }
            result.toByteArray().let { encoded ->
                if (definition.reversedStorage == true) ByteArray(encoded.size) { encoded[it].toInt().inv().toByte() } else encoded
            }
        }
        is ValueObjectDefinition<*, *> -> {
            require(bytes.size == definition.byteSize) { "Invalid current value-object width" }
            val result = bytes.copyOf()
            var offset = 0
            definition.dataModel.forEachIndexed { index, property ->
                if (index > 0) offset++
                val size = (property.definition as IsFixedStorageBytesEncodable<*>).byteSize
                property.definition.convertToReleasedFloatingPointBytes(bytes.copyOfRange(offset, offset + size)).copyInto(result, offset)
                offset += size
            }
            result
        }
        else -> bytes
    }
}

@Suppress("UNCHECKED_CAST")
private fun IsPropertyDefinition<*>.releasedStorageBytes(value: Any): ByteArray {
    val definition = if (this is IsDefinitionWrapper<*, *, *, *>) definition else this
    val storage = definition as IsStorageBytesEncodable<Any>
    val bytes = ByteArray(storage.calculateStorageByteLength(value))
    var offset = 0
    storage.writeStorageBytes(value) { bytes[offset++] = it }
    return definition.convertToReleasedFloatingPointBytes(bytes)
}

/** Reproduce the released reference codec, including its missing anonymous collection headers. */
fun AnyPropertyReference.toReleasedStorageByteArray(): ByteArray = buildList {
    for (node in unwrap()) {
        val component = when (node) {
            is MapValueReference<*, *, *> -> node.mapDefinition.keyDefinition.releasedStorageBytes(node.key)
            is SetItemReference<*, *> -> node.setDefinition.valueDefinition.releasedStorageBytes(node.value)
            else -> null
        }
        if (component == null) when (node) {
            is ListItemReference<*, *> -> node.index.writeBytes(::add)
            is TypedValueReference<*, *, *> -> node.type.index.writeVarIntWithExtraInfo(TYPE.value, ::add)
            else -> node.writeSelfStorageBytes(::add)
        }
        else {
            component.size.writeVarBytes(::add)
            component.forEach(::add)
        }
    }
}.toByteArray()

/** Reference interfaces have no context; copy stable node suffixes and encode only their key payloads contextually. */
@Suppress("UNCHECKED_CAST")
internal fun AnyPropertyReference.releasedTransportBytes(context: IsPropertyContext): ByteArray = buildList {
    for (node in unwrap()) {
        val payload = when (node) {
            is MapValueReference<*, *, *> -> Triple(node.mapDefinition.keyDefinition, node.key, 0)
            is MapKeyReference<*, *, *> -> Triple(node.propertyDefinition, node.key, 1)
            is SetItemReference<*, *> -> Triple(node.setDefinition.valueDefinition, node.value, 0)
            else -> null
        }
        if (payload != null) {
            val definition = payload.first as IsValueDefinition<Any, IsPropertyContext>
            val cache = WriteCache()
            definition.calculateTransportByteLengthWithKey(payload.third, payload.second, cache, context)
            definition.writeTransportBytesWithKey(payload.third, payload.second, cache, ::add, context)
        } else {
            val cache = WriteCache()
            val length = node.calculateTransportByteLength(cache)
            val current = ByteArray(length)
            var offset = 0
            node.writeTransportBytes(cache) { current[offset++] = it }
            val parentLength = (node as? IsPropertyReferenceWithParent<*, *, *, *>)?.parentReference
                ?.calculateTransportByteLength(WriteCache()) ?: 0
            for (index in parentLength until current.size) add(current[index])
        }
    }
}.toByteArray()
