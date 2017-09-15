package maryk.core.extensions.bytes

internal fun ByteArray.toBytes(destination: ByteArray, offset: Int = 0): ByteArray {
    this.forEachIndexed {
        index, byte -> destination[index + offset] = byte
    }
    return destination
}

internal fun initByteArray(byteArray: ByteArray, offset: Int = 0, length: Int = byteArray.size) = when {
    length == byteArray.size && offset == 0 -> byteArray
    else ->byteArray.copyOfRange(offset, offset + length)
}

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
internal fun initByteArray(reader: () -> Byte, length: Int) = ByteArray(length) {
    reader()
}