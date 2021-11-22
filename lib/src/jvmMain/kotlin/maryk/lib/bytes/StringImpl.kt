package maryk.lib.bytes

actual fun initString(bytes: ByteArray, offset: Int, length: Int) =
    bytes.decodeToString(offset, offset + length)

actual fun codePointAt(string: String, index: Int) = Character.codePointAt(string, index)

actual fun fromCodePoint(value: Int): String {
    return Character.toChars(value).joinToString(separator = "")
}
