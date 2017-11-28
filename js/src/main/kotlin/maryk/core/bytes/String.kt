package maryk.core.bytes

actual fun initString(length: Int, reader: () -> Byte): String {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
}

actual fun String.writeUTF8Bytes(writer: (byte: Byte) -> Unit) {}

/** Calculates the length of a String in UTF8 bytes in an optimized way
 * @param string to calculate length of
 * @throws IllegalArgumentException when string contains invalid UTF-16: unpaired surrogates
 */
actual fun String.calculateUTF8ByteLength(): Int {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
}