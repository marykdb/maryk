package maryk.core.extensions

import maryk.core.properties.exceptions.ParseException

private val hexChars = charArrayOf('0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f')

/** Converts ByteArray into a String Hex value */
internal fun ByteArray.toHex(): String {
    val numChars = size * 2
    val ch = CharArray(numChars)

    for (i in 0 until numChars step 2) {
        val d = this[i / 2].toInt()
        ch[i] = hexChars[d shr 4 and 0x0F]
        ch[i + 1] = hexChars[d and 0x0F]
    }
    return ch.joinToString("")
}

/** Converts [hex] String into a ByteArray */
internal fun initByteArrayByHex(hex: String) : ByteArray {
    if (hex.length % 2 != 0) { throw ParseException("length is not a multiple of 2") }
    val b = ByteArray(hex.length / 2)
    for (i in 0 until hex.length step 2) {
        b[i / 2] = (
                hexCharToInt(hex[i]) shl 4 or hexCharToInt(hex[i + 1])
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