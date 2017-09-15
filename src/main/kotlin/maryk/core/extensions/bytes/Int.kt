package maryk.core.extensions.bytes

import kotlin.experimental.and
import kotlin.experimental.xor

/** Converts Integers to unsigned byte array with more natural order
 * @param bytes    to add value to
 * @param offset to add value to
 * @return byte array
 */
internal fun Int.toBytes(bytes: ByteArray? = null, offset: Int = 0, length: Int = 4): ByteArray {
    if (length !in 1..4) { throw IllegalArgumentException("Length should be within range of 1 to 4") }

    val b = bytes ?: ByteArray(length)
    (offset + length - 1 downTo offset).forEachIndexed { i, it ->
        b[it] = (this shr i*8 and 0xFF).toByte()
    }

    when (length) {
        4 -> b[offset] = b[offset] xor SIGNBYTE // Reverse sign byte so byte order is more natural
        else -> // Check if value is not over length or negative
            (length until 4).forEach {
                if(this shr it*8 and 0xFF != 0) {
                    throw IllegalArgumentException("Number is outside the bounds for $length byte array")
                }
            }
    }
    return b
}

/** Converts byte array to unsigned Integer
 * @param bytes  to convertFromBytes
 * @param offset of byte to start
 * @return Integer represented by bytes
 */
internal fun initInt(bytes: ByteArray, offset: Int = 0, length: Int = 4): Int {
    var int = 0
    int shl 8 * (4 - length)
    (0 until length).forEach { i ->
        int = int shl 8
        int = int xor (bytes[i + offset].toInt() and 0xFF)
    }
    if (length == 4){ int += Int.MIN_VALUE }
    return int
}

/** Write the bytes of this Int to a writer
 * @param writer to write this Int to
 */
internal fun Int.writeBytes(writer: (byte: Byte) -> Unit, length: Int = 4) {
    if (length !in 3..4) { throw IllegalArgumentException("Length should be within range of 3 to 4") }

    (0 until length).forEach {
        val b = (this shr (length-1-it) * 8 and 0xFF).toByte()
        writer(
                if(it == 0) b xor SIGNBYTE else b
        )
    }
}

/** Converts reader with bytes to Int
 * @param reader to read bytes from
 * @return Int represented by bytes
 */
internal fun initInt(reader: () -> Byte, length: Int = 4): Int {
    var int = 0
    val firstByte = reader()
    // Skip bytes if below certain length
    if (length < 4) {
        val negative = firstByte and SIGNBYTE != SIGNBYTE
        (0 until 8 - length).forEach {
            if (negative) { // Set to max byte to have correct value if negative
                int = int xor 0xFF
            }
            int = int shl 8
        }
    }
    int = int xor ((firstByte xor SIGNBYTE).toInt() and 0xFF)
    (1 until length).forEach {
        int = int shl 8
        int = int xor (reader().toInt() and 0xFF)
    }
    return int
}