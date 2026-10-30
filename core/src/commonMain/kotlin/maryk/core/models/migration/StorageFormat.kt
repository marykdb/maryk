package maryk.core.models.migration

import maryk.core.extensions.bytes.calculateVarByteLength
import maryk.core.extensions.bytes.writeVarBytes
import maryk.core.processors.datastore.findByteIndexAndSizeByPartIndex
import maryk.core.properties.definitions.NumberDefinition
import maryk.core.properties.definitions.index.AnyOf
import maryk.core.properties.definitions.index.IsIndexable
import maryk.core.properties.definitions.index.Reversed
import maryk.core.properties.definitions.index.Multiple
import maryk.core.properties.definitions.index.componentDefinition
import maryk.core.properties.definitions.index.encodeIndexComponent
import maryk.core.properties.definitions.index.hasVariableIndexComponent
import maryk.core.properties.definitions.index.toOrderedFloatingPointBytes
import maryk.core.properties.types.numeric.Float32
import maryk.core.properties.types.numeric.Float64

/** Engine value and sort-key format, independent of application schema versions. */
object StorageFormat {
    const val LEGACY = 1
    const val CURRENT = 2
}

/** Fixed-width non-floating components can still have legacy bytes in their reference ancestors. */
fun IsIndexable.requiresStorageFormatUpgrade(): Boolean =
    !referenceStorageByteArray.bytes.contentEquals(toReleasedReferenceStorageByteArray()) || requiresKeyStorageFormatUpgrade()

/** Primary record keys contain component values, without their index descriptor. */
fun IsIndexable.requiresKeyStorageFormatUpgrade(): Boolean = hasVariableIndexComponent() || when (this) {
    is Multiple -> references.any { it.requiresKeyStorageFormatUpgrade() }
    is AnyOf -> references.any { it.requiresKeyStorageFormatUpgrade() }
    else -> componentDefinition()?.hasFloatingPointStorage() == true
}

/** Converts an index entry while preserving its record key and ordinary stored value bytes. */
fun IsIndexable.convertLegacyIndexBytes(bytes: ByteArray, keySize: Int): ByteArray {
    require(bytes.size >= keySize) { "Invalid legacy index entry" }
    val parts = if (this is Multiple) references else listOf(this)
    val components = parts.mapIndexed { index, part ->
        val (offset, size) = findByteIndexAndSizeByPartIndex(index, bytes, keySize, indexPartCount = parts.size)
        val source = bytes.copyOfRange(offset, offset + size)
        val component = if (part is AnyOf) part.references.first() else part
        // Reversal wraps both the ordinary storage codec and the component framing.
        val reversed = component is Reversed<*>
        val ordinary = if (reversed) ByteArray(source.size) { source[it].toInt().inv().toByte() } else source
        val current = component.componentDefinition()?.convertLegacyFloatingPointBytes(ordinary) ?: ordinary
        component.encodeIndexComponent(if (reversed) ByteArray(current.size) { current[it].toInt().inv().toByte() } else current)
    }
    val output = ByteArray(components.sumOf { it.size + it.size.calculateVarByteLength() } + keySize)
    var offset = 0
    for (component in components) {
        component.copyInto(output, offset)
        offset += component.size
    }
    for (component in components.asReversed()) component.size.writeVarBytes { output[offset++] = it }
    bytes.copyInto(output, offset, bytes.size - keySize)
    return output
}

/** Index-only NaN normalization; finite values already have ordered ordinary bytes. */
internal fun NumberDefinition<*>.toOrderedNumberBytes(bytes: ByteArray): ByteArray {
    if (type != Float32 && type != Float64) return bytes
    var offset = 0
    val value = readStorageBytes(bytes.size) { bytes[offset++] }
    return requireNotNull(toOrderedFloatingPointBytes(value))
}
