package maryk.core.extensions.bytes

import maryk.lib.exceptions.ParseException
import kotlin.experimental.and
import kotlin.experimental.xor

internal const val SIGN_BYTE: Byte = 0b1000_0000.toByte()
internal const val ZERO_BYTE: Byte = 0b0.toByte()
internal const val ONE_BYTE: Byte = 0b1.toByte()
internal const val MAX_BYTE: Byte = 0b1111_1111.toByte()
internal const val SEVEN_BYTES: Byte = 0b0111_1111.toByte()

/** Write the bytes of this Byte to a [writer] */
internal fun Byte.writeBytes(writer: (byte: Byte) -> Unit) {
    writer(
        this and MAX_BYTE xor SIGN_BYTE
    )
}

/** Creates a Byte by reading byte from [reader] */
internal fun initByte(reader: () -> Byte) = reader() xor SIGN_BYTE and MAX_BYTE

/**
 * Encodes the Byte in zigzag pattern so negative values are
 * able to encode much more efficiently into varInt
 */
internal fun Byte.encodeZigZag() = this.toInt().encodeZigZag().toByte()

/** Decodes the Short out of zigzag pattern so bytes have the normal native order again */
internal fun Byte.decodeZigZag() = (this.toInt() and 0xFF).decodeZigZag().toByte()

/** Write the bytes of this Int as a variable int to a [writer] */
internal fun Byte.writeVarBytes(writer: (byte: Byte) -> Unit) {
    if (this < 0) {
        writer(this and SEVEN_BYTES xor SIGN_BYTE)
        writer(1)
    } else {
        writer(this)
    }
}

/** Creates Byte by reading variable length encoded value from [reader] */
internal fun initByteByVar(reader: () -> Byte): Byte {
    var shift = 0
    var result = 0
    while (shift < 8) {
        val b = reader().toInt()
        result = result or ((b and 0x7F) shl shift)
        if (b and 0x80 == 0) {
            return result.toByte()
        }
        shift += 7
    }
    throw ParseException("Malformed varByte")
}

/** Calculates the byte length of the variable int */
internal fun Byte.calculateVarByteLength(): Int {
    val asInt = this.toInt()
    return when {
        asInt and (0xff shl 7) == 0 -> 1
        else -> 2
    }
}
