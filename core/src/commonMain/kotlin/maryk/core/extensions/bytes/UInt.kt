@file:Suppress("EXPERIMENTAL_API_USAGE", "EXPERIMENTAL_UNSIGNED_LITERALS")

package maryk.core.extensions.bytes

/** Write the bytes of this Int to a [writer] */
internal fun UInt.writeBytes(writer: (byte: Byte) -> Unit, length: Int = 4) {
    if (length !in 3..4) { throw IllegalArgumentException("Length should be within range of 3 to 4") }

    for (it in 0 until length) {
        val b = (this shr (length-1-it) * 8 and 0xFFu).toByte()
        writer(b)
    }
}

/** Creates Integer by reading bytes from [reader] */
internal fun initUInt(reader: () -> Byte, length: Int = 4): UInt {
    var int = 0u
    val firstByte = reader()
    // Skip bytes if below certain length
    if (length < 4) {
        for (it in 0 until 8 - length) {
            int = int shl 8
        }
    }
    int = int xor ((firstByte).toUInt() and 0xFFu)
    for (it in 1 until length) {
        int = int shl 8
        int = int xor (reader().toUInt() and 0xFFu)
    }
    return int
}
