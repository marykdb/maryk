package maryk.core.extensions.bytes

import kotlin.experimental.and
import kotlin.experimental.xor

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