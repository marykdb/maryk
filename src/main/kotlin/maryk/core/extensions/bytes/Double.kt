package maryk.core.extensions.bytes

import maryk.core.extensions.rawbytes.toRawLongBits

/** Converts Double to byte array
 * @param bytes    to add value to
 * @param offset to add value to
 * @return byte array
 */
internal fun Double.toBytes(bytes: ByteArray? = null, offset: Int = 0): ByteArray {
    var l = this.toRawLongBits()
    l = (l xor ((l shr 64 - 1) or Long.MIN_VALUE)) + 1
    return l.toBytes(bytes, offset)
}

/** Converts byte array to Double
 * @param bytes  to convertFromBytes
 * @param offset of byte to start
 * @return Double represented by bytes
 */
internal fun initDouble(bytes: ByteArray, offset: Int = 0): Double {
    var l = initLong(bytes, offset) - 1
    l = l xor (l.inv() shr 64 - 1 or java.lang.Long.MIN_VALUE)
    return maryk.core.extensions.rawbytes.initDouble(l)
}

/** Write the bytes of this Double to a writer
 * @param writer to write this Double to
 */
internal fun Double.writeBytes(writer: (byte: Byte) -> Unit) {
    var l = this.toRawLongBits()
    l = (l xor ((l shr 64 - 1) or Long.MIN_VALUE)) + 1
    return l.writeBytes(writer)
}

/** Converts reader with bytes to Double
 * @param reader to read bytes from
 * @return Double represented by bytes
 */
internal fun initDouble(reader: () -> Byte): Double {
    var l = initLong(reader) - 1
    l = l xor (l.inv() shr 64 - 1 or java.lang.Long.MIN_VALUE)
    return maryk.core.extensions.rawbytes.initDouble(l)
}