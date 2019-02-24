package maryk.lib.bytes

actual fun initString(length: Int, reader: () -> Byte) = ByteArray(length) {
    reader()
}.stringFromUtf8()

actual fun codePointAt(string: String, index: Int): Int {
    return Char.toCodePoint(string[index], string[index + 1])
}

actual fun fromCodePoint(value: Int) = String(Char.toChars(value))
