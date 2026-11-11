package maryk.datastore.shared

import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.definitions.StringDefinition

/** Decode canonical strings directly from an already bounded payload; retain the general
 * decoder for deletion/type markers, custom definitions and noncanonical representations. */
fun readStoredValue(
    definition: IsPropertyDefinition<out Any>?,
    bytes: ByteArray,
    offset: Int,
    length: Int,
): Any? {
    require(offset >= 0 && length >= 0 && offset <= bytes.size - length)
    if (definition is StringDefinition && length > 0 && bytes[offset] == TypeIndicator.NoTypeIndicator.byte) {
        return bytes.decodeToString(offset + 1, offset + length, throwOnInvalidSequence = true)
    }
    var index = offset
    val end = offset + length
    val reader: () -> Byte = if (end == bytes.size) {
        { bytes[index++] }
    } else {
        {
            check(index < end) { "Truncated stored value payload" }
            bytes[index++]
        }
    }
    return readValue(definition, reader) { end - index }
}
