package maryk.core.extensions.bytes

import kotlin.experimental.and
import kotlin.experimental.xor

const val MAX_SEVEN_VALUE = 256L*256L*256L*256L*256L*256L*128L-1
const val MIN_SEVEN_VALUE = 256L*256L*256L*256L*256L*256L*128L*-1

/** Write the bytes of this Long to a writer
 * @param writer to write this Long to
 */
fun Long.writeBytes(writer: (byte: Byte) -> Unit, length: Int = 8) {
    if (length !in 5..8) { throw IllegalArgumentException("Length should be within range of 5 to 8") }

    (0 until length).forEach {
        val b = (this shr (length-1-it) * 8 and 0xFF).toByte()
        writer(
                if(it == 0) b xor SIGNBYTE else b
        )
    }
}

/** Converts reader with bytes to Long
 * @param reader to read bytes from
 * @return Long represented by bytes
 */
internal fun initLong(reader: () -> Byte, length: Int = 8): Long {
    var long = 0L
    val firstByte = reader()
    // Skip bytes if below certain length
    if (length < 8) {
        val negative = firstByte and SIGNBYTE != SIGNBYTE
        (0 until 8 - length).forEach {
            if (negative) { // Set to max byte to have correct value if negative
                long = long xor 0xFF
            }
            long = long shl 8
        }
    }
    long = long xor ((firstByte xor SIGNBYTE).toLong() and 0xFF)
    (1 until length).forEach {
        long = long shl 8
        long = long xor (reader().toLong() and 0xFF)
    }
    return long
}