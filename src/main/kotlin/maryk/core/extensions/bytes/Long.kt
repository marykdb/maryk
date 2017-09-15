package maryk.core.extensions.bytes

import kotlin.experimental.and
import kotlin.experimental.or
import kotlin.experimental.xor

const val MAX_SEVEN_VALUE = 256L*256L*256L*256L*256L*256L*128L-1
const val MIN_SEVEN_VALUE = 256L*256L*256L*256L*256L*256L*128L*-1

internal fun Long.toSevenBytes(bytes: ByteArray? = null, offset: Int = 0): ByteArray {
    if (this !in MIN_SEVEN_VALUE..MAX_SEVEN_VALUE) {
        throw IllegalArgumentException("Number is outside the bounds for 7 byte array")
    }
    val b = bytes ?: ByteArray(7)

    b[offset] = (this.ushr(48).toByte() and 0x7F or (this.ushr(52).toByte() and SIGNBYTE))
    b[offset + 1] = this.ushr(40).toByte()
    b[offset + 2] = this.ushr(32).toByte()
    b[offset + 3] = this.ushr(24).toByte()
    b[offset + 4] = this.ushr(16).toByte()
    b[offset + 5] = this.ushr(8).toByte()
    b[offset + 6] = this.toByte()
    // Turn around signbyte for more natural ordering
    b[offset] = b[offset] xor SIGNBYTE
    return b
}

internal fun initLongSeven(bytes: ByteArray, offset: Int = 0): Long {
    var l: Long = 0

    (0..6).forEach { i ->
        l = l shl 8
        l = l xor (bytes[i + offset].toLong() and 0xFFL)
    }
    l += if (bytes[offset] and SIGNBYTE == SIGNBYTE) {
        -0x100000000000000L - MIN_SEVEN_VALUE
    } else MIN_SEVEN_VALUE
    return l
}

/**
 * Converts Integers to unsigned byte array with more natural order
 * @param bytes    to add value to
 * @param offset to add value to
 * @return byte array
 */
internal fun Long.toBytes(bytes: ByteArray? = null, offset: Int = 0, length: Int = 8): ByteArray {
    if (length !in 1..8) { throw IllegalArgumentException("Length should be within range of 1 to 8") }

    val b = bytes ?: ByteArray(length)
    (offset + length - 1 downTo offset).forEachIndexed { i, it ->
        b[it] = (this shr i*8 and 0xFF).toByte()
    }
    when {
        length == 8 -> b[offset] = b[offset] xor SIGNBYTE // Reverse sign byte so byte order is more natural
        else -> {
            (length until 8).forEach {
                if(this shr it*8 and 0xFF != 0L) {
                    throw IllegalArgumentException("Number is outside the bounds for $length byte array")
                }
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
internal fun initLong(bytes: ByteArray, offset: Int = 0, length: Int = 8): Long {
    var long = 0L
    long shl 8 * (8 - length)
    (0 until length).forEach { i ->
        long = long shl 8
        long = long xor (bytes[i + offset].toLong() and 0xFF)
    }
    if (length == 8){ long += Long.MIN_VALUE }
    return long
}

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