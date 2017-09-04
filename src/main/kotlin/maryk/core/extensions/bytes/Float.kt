package maryk.core.extensions.bytes

import maryk.core.extensions.rawbytes.toRawIntBits

/**
 * Converts Float to byte array
 * @param bytes    to add value to
 * @param offset to add value to
 * @return byte array
 */
fun Float.toBytes(bytes: ByteArray? = null, offset: Int = 0): ByteArray {
    var f = this.toRawIntBits()
    f = (f xor (f shr 32 - 1 or Integer.MIN_VALUE)) + 1
    return f.toBytes(bytes, offset)
}

/**
 * Converts byte array to Float
 * @param bytes  to convertFromBytes
 * @param offset of byte to start
 * @return Float represented by bytes
 */
fun initFloat(bytes: ByteArray, offset: Int = 0): Float {
    var f = initInt(bytes, offset) - 1
    f = f xor (f.inv() shr 32 - 1 or Integer.MIN_VALUE)
    return maryk.core.extensions.rawbytes.initFloat(f)
}
