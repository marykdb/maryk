package maryk.core.extensions

private val lineBreakChars = charArrayOf('\r', '\n')

fun Char.isLineBreak() = this in lineBreakChars

private val spacingChars = charArrayOf(' ', '\t')

fun Char.isSpacing() = this in spacingChars