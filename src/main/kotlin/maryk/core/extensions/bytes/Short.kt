package maryk.core.extensions.bytes

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