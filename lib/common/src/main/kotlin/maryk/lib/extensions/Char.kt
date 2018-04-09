package maryk.lib.extensions

internal val lineBreakChars = charArrayOf('\r', '\n')
fun Char.isLineBreak() = this in lineBreakChars

internal val spacingChars = charArrayOf(' ', '\t')
fun Char.isSpacing() = this in spacingChars

internal val digitChars = charArrayOf('0', '1', '2', '3', '4', '5', '6', '7', '8', '9')
fun Char.isDigit() = this in digitChars

internal val digitCharsWithoutZero = charArrayOf('1', '2', '3', '4', '5', '6', '7', '8', '9')
fun Char.isNonZeroDigit() = this in digitCharsWithoutZero
