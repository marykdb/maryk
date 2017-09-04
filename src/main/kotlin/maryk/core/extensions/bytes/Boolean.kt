package maryk.core.extensions.bytes

/**
 * Converts Boolean to byte array
 * @param bytes    to add value to
 * @param offset to add value to
 * @return byte array
 */
fun Boolean.toBytes(bytes: ByteArray? = null, offset: Int = 0): ByteArray {
    val b = bytes ?: ByteArray(1)
    b[offset] = if (this) 1 else 0
    return b
}

/**
 * Converts byte array to Boolean
 * @param bytes  to convertFromBytes
 * @param offset of byte to start
 * @return Boolean represented by bytes
 */
fun initBoolean(bytes: ByteArray, offset: Int = 0): Boolean {
    return bytes[offset] != 0.toByte()
}