package maryk.core.extensions.bytes

import maryk.core.properties.exceptions.ParseException
import kotlin.experimental.and
import kotlin.experimental.or
import kotlin.experimental.xor

const val SIGNBYTE: Byte = 0x80.toByte()
const val ZEROBYTE: Byte = 0.toByte()
const val ONEBYTE: Byte = 1.toByte()
const val MAXBYTE: Byte = 0xFF.toByte()

/** Write the bytes of this Byte to a writer
 * @param writer to write this Byte to
 */
internal fun Byte.writeBytes(writer: (byte: Byte) -> Unit) {
        writer(
                this and MAXBYTE xor SIGNBYTE
        )
}

/** Converts reader with bytes to Byte
 * @param reader to read bytes from
 * @return Byte represented by bytes
 */
internal fun initByte(reader: () -> Byte) = reader() xor SIGNBYTE and MAXBYTE

/** Encodes the Byte in zigzag pattern so negative values are
 * able to encode much more efficiently into varInt
 */
internal fun Byte.encodeZigZag() = this.toInt().encodeZigZag().toByte()

/** Decodes the Short out of zigzag pattern so bytes have the normal native order again */
internal fun Byte.decodeZigZag() = (this.toInt() and 0xFF).decodeZigZag().toByte()

/** Write the bytes of this Int as a variable int to a writer
 * @param writer to write this Int to
 */
internal fun Byte.writeVarBytes(writer: (byte: Byte) -> Unit) {
    if (this < 0) {
        writer(this and 0x7F or SIGNBYTE)
        writer(1)
    } else {
        writer(this)
    }
}

/** Converts reader with var bytes to Byte
 * @param reader to read bytes from
 * @return Byte represented by bytes
 */
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
    throw ParseException("Malformed valInt")
}

/** Computes the byte size of the variable int */
internal fun Byte.computeVarByteSize(): Int {
    val asInt = this.toInt()
    return when {
        asInt and (0xff shl 7) == 0 -> 1
        else -> 2
    }
}