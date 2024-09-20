package maryk.lib.extensions

internal val lineBreakChars = setOf('\r', '\n')
fun Char.isLineBreak() = this in lineBreakChars

internal val spacingChars = setOf(' ', '\t')
fun Char.isSpacing() = this in spacingChars
