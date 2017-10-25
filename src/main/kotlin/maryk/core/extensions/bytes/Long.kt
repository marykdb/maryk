package maryk.core.extensions.bytes

import maryk.core.properties.exceptions.ParseException
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


/** Write the bytes of this Long as a variable int to a writer
 * @param writer to write this Int to
 */
internal fun Long.writeVarBytes(writer: (byte: Byte) -> Unit) {
    var value = this
    while (true) {
        if (value and 0x7F.inv() == 0L) {
            writer(value.toByte())
            return
        } else {
            writer((value and 0x7F or 0x80).toByte())
            value = value ushr 7
        }
    }
}

/** Encodes the Long in zigzag pattern so negative values are
 * able to encode much more efficiently into varInt
 */
internal fun Long.encodeZigZag() = this shl 1 xor (this shr 63)

/** Decodes the Long out of zigzag pattern so bytes have the normal native order again */
internal fun Long.decodeZigZag() = this ushr 1 xor -(this and 1)

/** Converts reader with var bytes to Long
 * @param reader to read bytes from
 * @return Int represented by bytes
 */
internal fun initLongByVar(reader: () -> Byte): Long {
    var shift = 0
    var result = 0L
    while (shift < 64) {
        val b = reader().toLong()
        result = result or ((b and 0x7FL) shl shift)
        if (b and 0x80L == 0L) {
            return result
        }
        shift += 7
    }
    throw ParseException("Malformed varInt")
}

/** Calculates the byte length of the variable int */
internal fun Long.calculateVarByteLength(): Int = when {
    this and (Long.MAX_VALUE shl 7) == 0L -> 1
    this and (Long.MAX_VALUE shl 14) == 0L -> 2
    this and (Long.MAX_VALUE shl 21) == 0L -> 3
    this and (Long.MAX_VALUE shl 28) == 0L -> 4
    this and (Long.MAX_VALUE shl 35) == 0L -> 5
    this and (Long.MAX_VALUE shl 42) == 0L -> 6
    this and (Long.MAX_VALUE shl 49) == 0L -> 7
    this and (Long.MAX_VALUE shl 56) == 0L -> 8
    this and (Long.MAX_VALUE shl 63) == 0L -> 9
    else -> 10
}
