package maryk.datastore.shared

import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.definitions.StringDefinition
import maryk.lib.bytes.decodeUtf8Strict

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
        return bytes.decodeUtf8Strict(offset + 1, offset + length)
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
