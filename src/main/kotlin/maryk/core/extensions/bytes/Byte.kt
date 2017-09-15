package maryk.core.extensions.bytes

import kotlin.experimental.and
import kotlin.experimental.xor

const val SIGNBYTE: Byte = 0x80.toByte()
const val ZEROBYTE: Byte = 0.toByte()
const val ONEBYTE: Byte = 1.toByte()
const val MAXBYTE: Byte = 0xFF.toByte()

/** Converts Integers to unsigned byte array with more natural order
 * @param bytes    to add value to
 * @param offset to add value to
 * @return byte array
 */
internal fun Byte.toBytes(bytes: ByteArray? = null, offset: Int = 0): ByteArray {
    val b = bytes ?: ByteArray(1)
    (offset downTo offset).forEachIndexed { i, it ->
        b[it] = (this.toInt() shr i*8 and 0xFF).toByte()
    }
    b[offset] = b[offset] xor SIGNBYTE // Reverse sign byte so byte order is more natural
    return b
}

/** Converts byte array to unsigned Integer
 * @param bytes  to convertFromBytes
 * @param offset of byte to start
 * @return Integer represented by bytes
 */
internal fun initByte(bytes: ByteArray, offset: Int = 0): Byte {
    var byte = 0
    byte = byte shl 8
    byte = byte xor (bytes[offset].toInt() and 0xFF)
    byte += Byte.MIN_VALUE
    return byte.toByte()
}

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
