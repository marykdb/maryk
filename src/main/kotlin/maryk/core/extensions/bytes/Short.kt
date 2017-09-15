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