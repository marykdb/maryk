package maryk.core.extensions.bytes

import kotlin.experimental.and
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
