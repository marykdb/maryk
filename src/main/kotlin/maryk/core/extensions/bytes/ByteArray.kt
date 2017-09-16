package maryk.core.extensions.bytes

/** Write the bytes of this ByteArray to a writer
 * @param writer to write this ByteArray to
 */
internal fun ByteArray.writeBytes(writer: (byte: Byte) -> Unit) {
    this.forEach {
        writer(it)
    }
}

/** Converts reader with bytes to ByteArray
 * @param reader to read bytes from
 * @return ByteArray represented by bytes
 */
internal fun initByteArray(length: Int, reader: () -> Byte) = ByteArray(length) {
    reader()
}