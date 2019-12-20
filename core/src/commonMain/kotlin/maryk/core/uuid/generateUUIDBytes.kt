package maryk.core.uuid

import maryk.core.extensions.bytes.writeBytes
import maryk.lib.uuid.generateUUID

/**
 * Generates UUID ByteArray
 */
fun generateUUIDBytes(): ByteArray {
    val bytes = ByteArray(16)
    var writeIndex = 0
    val writer: (Byte) -> Unit = { bytes[writeIndex++] = it }
    val uuid = generateUUID()

    uuid.first.writeBytes(writer)
    uuid.second.writeBytes(writer)
    return bytes
}
