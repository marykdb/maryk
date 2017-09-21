package maryk.core.extensions.bytes

import maryk.core.properties.exceptions.ParseException
import kotlin.experimental.and
import kotlin.experimental.xor

/** Write the bytes of this Short to a writer
 * @param writer to write this Short to
 */
internal fun Short.writeBytes(writer: (byte: Byte) -> Unit) {
    (0 until 2).forEach {
        val b = (this.toInt() shr (1-it) * 8 and 0xFF).toByte()
        writer(
                if(it == 0) b xor SIGNBYTE else b
        )
    }
}

/** Converts reader with bytes to Short
 * @param reader to read bytes from
 * @return Short represented by bytes
 */
internal fun initShort(reader: () -> Byte): Short {
    var short = ((reader() xor SIGNBYTE).toInt() and 0xFF)
    short = short shl 8
    short = short xor (reader().toInt() and 0xFF)
    return short.toShort()
}

/** Encodes the Short in zigzag pattern so negative values are
 * able to encode much more efficiently into varInt
 */
internal fun Short.encodeZigZag() = this.toInt().encodeZigZag().toShort()

/** Decodes the Short out of zigzag pattern so bytes have the normal native order again */
internal fun Short.decodeZigZag() = (this.toInt() and 0xFFFF).decodeZigZag().toShort()

/** Write the bytes of this Int as a variable int to a writer
 * @param writer to write this Int to
 */
internal fun Short.writeVarBytes(writer: (byte: Byte) -> Unit) {
    var value = this.toInt()
    if (value < 0) {
        value = value and 0x7fff or 0x8000
    }
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

/** Converts reader with var bytes to Int
 * @param reader to read bytes from
 * @return Int represented by bytes
 */
internal fun initShortByVar(reader: () -> Byte): Short {
    var shift = 0
    var result = 0
    while (shift < 16) {
        val b = (reader() and MAXBYTE)
        result = result or ((b and 0x7F).toInt() shl shift)
        if (b and SIGNBYTE == ZEROBYTE) {
            return result.toShort()
        }
        shift += 7
    }
    throw ParseException("Malformed valInt")
}

/** Computes the byte size of the variable int */
internal fun Short.computeVarByteSize(): Int {
    val asInt = this.toInt()
    return when {
        asInt and (0xffff shl 7) == 0 -> 1
        asInt and (0xffff shl 14) == 0 -> 2
        else -> 3
    }
}