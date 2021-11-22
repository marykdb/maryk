package maryk.lib.bytes

actual fun initString(bytes: ByteArray, offset: Int, length: Int) =
    bytes.decodeToString(offset, offset + length)

actual fun codePointAt(string: String, index: Int): Int {
    return Char.toCodePoint(string[index], string[index + 1])
}

actual fun fromCodePoint(value: Int) = Char.toChars(value).concatToString()
