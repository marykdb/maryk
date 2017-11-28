package maryk.core.bytes;

expect fun initString(length: Int, reader: () -> Byte): String

expect fun String.writeUTF8Bytes(writer: (byte: Byte) -> Unit)

/** Calculates the length of a String in UTF8 bytes in an optimized way
 * @param string to calculate length of
 * @throws IllegalArgumentException when string contains invalid UTF-16: unpaired surrogates
 */
expect fun String.calculateUTF8ByteLength(): Int
