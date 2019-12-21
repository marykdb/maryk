package maryk.lib.bytes

actual fun initString(bytes: ByteArray, offset: Int, length: Int) =
    bytes.decodeToString(offset, offset + length)

actual fun initString(length: Int, reader: () -> Byte) = ByteArray(length) {
    reader()
}.decodeToString()

actual fun codePointAt(string: String, index: Int): Int {
    return Char.toCodePoint(string[index], string[index + 1])
}

actual fun fromCodePoint(value: Int) = String(Char.toChars(value))
