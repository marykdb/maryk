package maryk.core.extensions.bytes

import kotlin.experimental.xor

/** Converts Integers to unsigned byte array with more natural order
 * @param bytes    to add value to
 * @param offset to add value to
 * @return byte array
 */
internal fun Short.toBytes(bytes: ByteArray? = null, offset: Int = 0): ByteArray {
    val b = bytes ?: ByteArray(2)
    (offset + 1 downTo offset).forEachIndexed { i, it ->
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
internal fun initShort(bytes: ByteArray, offset: Int = 0): Short {
    var short = 0
    (0 until 2).forEach { i ->
        short = short shl 8
        short = short xor (bytes[i + offset].toInt() and 0xFF)
    }
    short += Short.MIN_VALUE
    return short.toShort()
}
