package maryk.core.properties.definitions.index

import maryk.core.extensions.bytes.calculateVarByteLength
import maryk.core.extensions.bytes.writeVarBytes
import maryk.core.extensions.bytes.writeOrderedBytes
import maryk.core.models.migration.toOrderedNumberBytes
import maryk.core.properties.definitions.NumberDefinition
import maryk.core.properties.definitions.IsFixedStorageBytesEncodable
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.definitions.wrapper.IsDefinitionWrapper
import maryk.core.properties.references.IsIndexablePropertyReference
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.references.MapAnyKeyReference
import maryk.core.properties.references.SetAnyValueReference
import maryk.core.values.IsValuesGetter
import maryk.core.properties.types.numeric.Float32
import maryk.core.properties.types.numeric.Float64
import maryk.lib.exceptions.ParseException

/** Fixed-width components retain their existing index representation. */
internal fun IsIndexable.componentDefinition(): IsPropertyDefinition<*>? = when (this) {
    is Reversed<*> -> reference.componentDefinition()
    is Normalize -> reference.componentDefinition()
    is LegacyNormalize -> reference.componentDefinition()
    is Split -> reference.componentDefinition()
    is ReferenceToMax<*> -> reference.componentDefinition()
    is MapAnyKeyReference<*, *, *> -> mapDefinition.keyDefinition
    is SetAnyValueReference<*, *> -> setDefinition.valueDefinition
    is IsPropertyReference<*, *, *> -> propertyDefinition.let {
        if (it is IsDefinitionWrapper<*, *, *, *>) it.definition else it
    }
    else -> null
}

/** Whether the component's ordered bytes change in storage format 2. */
fun IsIndexable.hasVariableIndexComponent(): Boolean = when (this) {
    is Multiple -> references.any { it.hasVariableIndexComponent() }
    is AnyOf -> true
    else -> componentDefinition()?.let { it !is IsFixedStorageBytesEncodable<*> } ?: false
}

/** Encode numeric index bytes from the value, without visiting the ordinary value codec. */
internal fun NumberDefinition<*>.toOrderedFloatingPointBytes(value: Any, reversed: Boolean = false): ByteArray? {
    if (type != Float32 && type != Float64) return null
    val output = ByteArray(type.size)
    var offset = 0
    val invert = (reversedStorage == true) != reversed
    val writer: (Byte) -> Unit = { byte -> output[offset++] = if (invert) byte.toInt().inv().toByte() else byte }
    // Float and Double have the same runtime type on JS; use the declared type.
    when (type) {
        Float32 -> (value as Float).writeOrderedBytes(writer)
        Float64 -> (value as Double).writeOrderedBytes(writer)
    }
    return output
}

internal fun <T : Any> IsIndexablePropertyReference<T>.encodeIndexValue(value: T): ByteArray =
    (componentDefinition() as? NumberDefinition<*>)?.toOrderedFloatingPointBytes(value, reversed = this is Reversed<*>)
        ?: encodeIndexComponent(toStorageBytes(value))

/** Encode boundaries as part of the component, before any following component or length. */
internal fun IsIndexable.encodeIndexComponent(bytes: ByteArray, partial: Boolean = false): ByteArray {
    val definition = componentDefinition() ?: return bytes
    val reversed = this is Reversed<*>
    if (definition is IsFixedStorageBytesEncodable<*> && definition !is NumberDefinition<*>) return bytes
    val source = if (reversed) ByteArray(bytes.size) { bytes[it].toInt().inv().toByte() } else bytes
    if (definition is NumberDefinition<*>) {
        val ordered = definition.toOrderedNumberBytes(source)
        return if (reversed) ByteArray(ordered.size) { ordered[it].toInt().inv().toByte() } else ordered
    }
    val ordered = if (definition is StringDefinition) {
        val text = source.decodeToString(throwOnInvalidSequence = true)
        ByteArray(text.length * 2).also { output ->
            text.forEachIndexed { index, char ->
                output[index * 2] = (char.code ushr 8).toByte()
                output[index * 2 + 1] = char.code.toByte()
            }
        }
    } else source
    val result = ByteArray(ordered.size + ordered.count { it == 0.toByte() } + if (partial) 0 else 2)
    var index = 0
    for (byte in ordered) {
        result[index++] = byte
        if (byte == 0.toByte()) result[index++] = 0xff.toByte()
    }
    if (reversed) result.indices.forEach { result[it] = result[it].toInt().inv().toByte() }
    return result
}

/** Decodes the string component selected by a regex matcher, excluding suffix lengths. */
internal fun decodeIndexString(bytes: ByteArray, offset: Int, size: Int): String {
    if (size < 2) throw ParseException("Missing string index terminator")
    val reversed = bytes[offset + size - 1] == 0xff.toByte() && bytes[offset + size - 2] == 0xff.toByte()
    fun read(index: Int) = bytes[offset + index].toInt().and(255).let { if (reversed) it xor 255 else it }
    if (read(size - 1) != 0 || read(size - 2) != 0) throw ParseException("Invalid string index terminator")
    val decoded = mutableListOf<Int>()
    var index = 0
    while (index < size - 2) {
        val byte = read(index++)
        if (byte == 0 && (index >= size - 2 || read(index++) != 255)) {
            throw ParseException("Invalid string index escape")
        }
        decoded += byte
    }
    if (decoded.size % 2 != 0) throw ParseException("Invalid string index code unit")
    return buildString(decoded.size / 2) {
        for (charIndex in decoded.indices step 2) append(((decoded[charIndex] shl 8) or decoded[charIndex + 1]).toChar())
    }
}

internal fun IsIndexable.forEachIndexComponent(values: IsValuesGetter, emit: (ByteArray) -> Unit) {
    when (this) {
        is AnyOf -> references.forEach { it.forEachIndexComponent(values, emit) }
        is MapAnyKeyReference<*, *, *> -> forEachEncodedIndexComponent(values, emit)
        is SetAnyValueReference<*, *> -> forEachEncodedIndexComponent(values, emit)
        else -> {
            val definition = componentDefinition() as? NumberDefinition<*>
            if (this is IsIndexablePropertyReference<*> && (definition?.type == Float32 || definition?.type == Float64)) {
                indexComponentFromValue(values)?.let(emit)
            } else {
                forEachStorageByteArray(values) { emit(encodeIndexComponent(it)) }
            }
        }
    }
}

private fun <T : Any> IsIndexablePropertyReference<T>.indexComponentFromValue(values: IsValuesGetter) =
    getValueOrNull(values)?.let { encodeIndexValue(it) }

internal fun IsIndexable.indexComponents(values: IsValuesGetter): List<ByteArray> = when (this) {
    is Multiple -> references.flatMap { it.indexComponents(values) }
    else -> buildList { forEachIndexComponent(values, ::add) }
}

/** Builds one index entry from a collection key/item without changing its qualifier encoding. */
fun <T : Any> IsIndexablePropertyReference<T>.toStorageByteArrayForIndexValue(value: T, key: ByteArray): ByteArray {
    val component = encodeIndexValue(value)
    val output = ByteArray(component.size + component.size.calculateVarByteLength() + key.size)
    component.copyInto(output)
    var offset = component.size
    component.size.writeVarBytes { output[offset++] = it }
    key.copyInto(output, offset)
    return output
}
