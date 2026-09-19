package maryk.lib.extensions

fun Char.isLineBreak() = this == '\r' || this == '\n'

fun Char.isSpacing() = this == ' ' || this == '\t'

fun Char.isLowerHexChar() = this in '0'..'9' || this in 'a'..'f'
