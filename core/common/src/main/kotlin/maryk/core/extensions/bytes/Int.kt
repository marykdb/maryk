package maryk.core.extensions.bytes

import maryk.lib.exceptions.ParseException
import kotlin.experimental.and
import kotlin.experimental.xor

/** Write the bytes of this Int to a [writer] */
internal fun Int.writeBytes(writer: (byte: Byte) -> Unit, length: Int = 4) {
    if (length !in 3..4) { throw IllegalArgumentException("Length should be within range of 3 to 4") }

    for (it in 0 until length) {
        val b = (this shr (length-1-it) * 8 and 0xFF).toByte()
        writer(
            if(it == 0) b xor SIGN_BYTE else b
        )
    }
}

/** Creates Integer by reading bytes from [reader] */
internal fun initInt(reader: () -> Byte, length: Int = 4): Int {
    var int = 0
    val firstByte = reader()
    // Skip bytes if below certain length
    if (length < 4) {
        val negative = firstByte and SIGN_BYTE != SIGN_BYTE
        (0 until 8 - length).forEach {
            if (negative) { // Set to max byte to have correct value if negative
                int = int xor 0xFF
            }
            int = int shl 8
        }
    }
    int = int xor ((firstByte xor SIGN_BYTE).toInt() and 0xFF)
    for (it in 1 until length) {
        int = int shl 8
        int = int xor (reader().toInt() and 0xFF)
    }
    return int
}

/**
 * Encodes the Int in zigzag pattern so negative values are
 * able to encode much more efficiently into varInt
 */
internal fun Int.encodeZigZag() = this shl 1 xor (this shr 31)

/** Decodes the Int out of zigzag pattern so bytes have the normal native order again */
internal fun Int.decodeZigZag() = this ushr 1 xor -(this and 1)

/** Write the bytes of this Int as a variable int to a [writer] */
internal fun Int.writeVarBytes(writer: (byte: Byte) -> Unit) {
    var value = this
    while (true) {
        if (value and 0x7F.inv() == 0) {
            writer(value.toByte())
            return
        } else {
            writer((value and 0x7F or 0x80).toByte())
            value = value ushr 7
        }
    }
}

/** Creates Integer by reading bytes encoded with variable length from [reader] */
internal fun initIntByVar(reader: () -> Byte): Int {
    var shift = 0
    var result = 0
    while (shift < 32) {
        val b = reader().toInt()
        result = result or ((b and 0x7F) shl shift)
        if (b and 0x80 == 0) {
            return result
        }
        shift += 7
    }
    throw ParseException("Malformed valInt")
}

/** Calculates the byte length of the variable int */
internal fun Int.calculateVarByteLength(): Int = when {
    this and (Int.MAX_VALUE shl 7) == 0 -> 1
    this and (Int.MAX_VALUE shl 14) == 0 -> 2
    this and (Int.MAX_VALUE shl 21) == 0 -> 3
    this and (Int.MAX_VALUE shl 28) == 0 -> 4
    else -> 5
}

/** Write the bytes of this Int in little endian order to a [writer] */
internal fun Int.writeLittleEndianBytes(writer: (byte: Byte) -> Unit) {
    for (it in 0..3) {
        writer(
            (this shr it * 8 and 0xFF).toByte()
        )
    }
}

/** Creates Integer by reading bytes in little endian order from [reader] */
internal fun initIntLittleEndian(reader: () -> Byte) =
    (reader().toInt() and 0xff) or
    ((reader().toInt() and 0xff) shl 8) or
    ((reader().toInt() and 0xff) shl 16) or
    ((reader().toInt() and 0xff) shl 24)
