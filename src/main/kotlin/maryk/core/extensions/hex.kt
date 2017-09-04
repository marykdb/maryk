package maryk.core.extensions

import maryk.core.properties.exceptions.ParseException

private val HEX_CHARS = charArrayOf('0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f')

fun ByteArray.toHex(): String {
    val numChars = size * 2
    val ch = CharArray(numChars)

    (0 until numChars step 2).forEach {
        val d = this[it / 2].toInt()
        ch[it] = HEX_CHARS[d shr 4 and 0x0F]
        ch[it + 1] = HEX_CHARS[d and 0x0F]
    }
    return String(ch)
}

fun initByteArrayByHex(hex: String) : ByteArray {
    if (hex.length % 2 != 0) { throw ParseException("length is not a multiple of 2") }
    val b = ByteArray(hex.length / 2)
    (0 until hex.length step 2).forEach {
        b[it / 2] = (
                hexCharToInt(hex[it]) shl 4 or hexCharToInt(hex[it + 1])
                ).toByte()
    }
    return b
}

private fun hexCharToInt(ch: Char) = when (ch) {
    in '0'..'9' -> ch - '0'
    in 'a'..'f' -> ch - 'a' + 10
    in 'A'..'F' -> ch - 'A' + 10
    else -> throw ParseException("Invalid hex char: $ch")
}