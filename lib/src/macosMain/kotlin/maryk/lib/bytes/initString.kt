package maryk.lib.bytes

actual fun codePointAt(string: String, index: Int): Int {
    return Char.toCodePoint(string[index], string[index + 1])
}

actual fun fromCodePoint(value: Int) = Char.toChars(value).concatToString()
