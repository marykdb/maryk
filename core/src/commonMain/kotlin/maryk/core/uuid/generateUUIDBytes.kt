package maryk.core.uuid

import maryk.core.extensions.bytes.writeBytes
import maryk.lib.uuid.generateUUID
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

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

@OptIn(ExperimentalEncodingApi::class)
fun generateUUIDBase64(): String = Base64.UrlSafe.encode(generateUUIDBytes())
