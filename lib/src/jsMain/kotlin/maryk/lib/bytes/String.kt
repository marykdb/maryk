package maryk.lib.bytes

actual fun fromCodePoint(value: Int) = js("String.fromCodePoint(value)") as String

actual fun codePointAt(string: String, index: Int) = (js("string.codePointAt(index)") as Int)
