package maryk.lib.bytes

actual fun fromCodePoint(value: Int) = js("String.fromCodePoint(value)") as String

actual fun codePointAt(string: String, index: Int) = (js("string.codePointAt(index)") as Int)

actual fun initString(bytes: ByteArray, offset: Int, length: Int): String =
    bytes.decodeToString(offset, offset + length)
