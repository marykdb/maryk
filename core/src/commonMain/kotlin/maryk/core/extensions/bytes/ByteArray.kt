package maryk.core.extensions.bytes

import kotlin.experimental.xor

/** Write the bytes of this ByteArray to a [writer] */
internal fun ByteArray.writeBytes(writer: (byte: Byte) -> Unit) {
    for (it in this) {
        writer(it)
    }
}

/** Creates ByteArray by reading bytes from [reader] */
internal fun initByteArray(length: Int, reader: () -> Byte) = ByteArray(length) {
    reader()
}

/**
 * Inverts the current byte array
 * Optionally with [startIndex] and [endIndex]
 */
fun ByteArray.invert(startIndex: Int = 0, endIndex: Int = this.lastIndex): ByteArray {
    for (index in startIndex..endIndex) {
        this[index] = this[index] xor -1
    }
    return this
}
