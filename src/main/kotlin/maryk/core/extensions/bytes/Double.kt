package maryk.core.extensions.bytes

import maryk.core.extensions.rawbytes.toRawLongBits

/** Converts Double to byte array
 * @param bytes    to add value to
 * @param offset to add value to
 * @return byte array
 */
fun Double.toBytes(bytes: ByteArray? = null, offset: Int = 0): ByteArray {
    var l = this.toRawLongBits()
    l = (l xor ((l shr 64 - 1) or Long.MIN_VALUE)) + 1
    return l.toBytes(bytes, offset)
}

/** Converts byte array to Double
 * @param bytes  to convertFromBytes
 * @param offset of byte to start
 * @return Double represented by bytes
 */
fun initDouble(bytes: ByteArray, offset: Int = 0): Double {
    var l = initLong(bytes, offset) - 1
    l = l xor (l.inv() shr 64 - 1 or java.lang.Long.MIN_VALUE)
    return maryk.core.extensions.rawbytes.initDouble(l)
}
