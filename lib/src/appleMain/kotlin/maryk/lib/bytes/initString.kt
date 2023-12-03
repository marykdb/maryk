package maryk.lib.bytes

import kotlin.experimental.ExperimentalNativeApi

@OptIn(ExperimentalNativeApi::class)
actual fun codePointAt(string: String, index: Int): Int {
    return Char.toCodePoint(string[index], string[index + 1])
}

@OptIn(ExperimentalNativeApi::class)
actual fun fromCodePoint(value: Int) = Char.toChars(value).concatToString()
