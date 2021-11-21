package maryk.lib.extensions

internal val lineBreakChars = charArrayOf('\r', '\n')
fun Char.isLineBreak() = this in lineBreakChars

internal val spacingChars = charArrayOf(' ', '\t')
fun Char.isSpacing() = this in spacingChars
