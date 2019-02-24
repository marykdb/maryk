package maryk.lib.bytes

actual fun initString(length: Int, reader: () -> Byte) = String(
    ByteArray(length) {
        reader()
    }
)

actual fun codePointAt(string: String, index: Int) = Character.codePointAt(string, index)

actual fun fromCodePoint(value: Int): String {
    return Character.toChars(value).joinToString(separator = "")
}
