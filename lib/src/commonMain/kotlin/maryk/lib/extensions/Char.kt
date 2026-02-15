package maryk.lib.extensions

fun Char.isLineBreak() = this == '\r' || this == '\n'

fun Char.isSpacing() = this == ' ' || this == '\t'
