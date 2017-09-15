package maryk.core.extensions.bytes

/**
 * Converts Boolean to byte array
 * @param bytes    to add value to
 * @param offset to add value to
 * @return byte array
 */
internal fun Boolean.toBytes(bytes: ByteArray? = null, offset: Int = 0): ByteArray {
    val b = bytes ?: ByteArray(1)
    b[offset] = if (this) ONEBYTE else ZEROBYTE
    return b
}

/**
 * Converts byte array to Boolean
 * @param bytes  to convertFromBytes
 * @param offset of byte to start
 * @return Boolean represented by bytes
 */
internal fun initBoolean(bytes: ByteArray, offset: Int = 0): Boolean {
    return bytes[offset] != ZEROBYTE
}

/** Write the bytes of this Boolean to a writer
 * @param writer to write this Boolean to
 */
internal fun Boolean.writeBytes(writer: (byte: Byte) -> Unit) {
    writer(
            if (this) ONEBYTE else ZEROBYTE
    )
}

/** Converts reader with bytes to Boolean
 * @param reader to read bytes from
 * @return Boolean represented by bytes
 */
internal fun initBoolean(reader: () -> Byte) = reader() != ZEROBYTE