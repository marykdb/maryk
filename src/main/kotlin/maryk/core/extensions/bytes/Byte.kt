package maryk.core.extensions.bytes

import kotlin.experimental.xor

const val SIGNBYTE: Byte = 0x80.toByte()

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
