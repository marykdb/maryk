package maryk.lib.extensions

import maryk.lib.exceptions.ParseException

const val ZERO_BYTE: Byte = 0b0
val HEX_CHARS = charArrayOf('0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f')

/** Converts ByteArray into a String Hex value */
fun ByteArray.toHex(skipLeadingZeroBytes: Boolean = false): String {
    val numChars = size * 2

    val startPos = if (skipLeadingZeroBytes) {
        var index = 0
        while (this[index] == ZERO_BYTE) {
            index++
            if (index >= this.size) {
                return ""
            }
        }
        index * 2
    } else 0

    val charArray = CharArray(numChars - startPos)

    for (i in startPos until numChars step 2) {
        val d = this[i / 2].toInt()
        charArray[i - startPos] = HEX_CHARS[d shr 4 and 0x0F]
        charArray[i - startPos + 1] = HEX_CHARS[d and 0x0F]
    }

    return charArray.concatToString()
}

/** Converts [hex] String into a ByteArray */
fun initByteArrayByHex(hex: String): ByteArray {
    if (hex.length % 2 != 0) {
        throw ParseException("length is not a multiple of 2")
    }
    val b = ByteArray(hex.length / 2)
    for (i in hex.indices step 2) {
        b[i / 2] = (hexCharToInt(hex[i]) shl 4 or hexCharToInt(hex[i + 1])).toByte()
    }
    return b
}

private fun hexCharToInt(ch: Char) = ch.digitToIntOrNull(16)
    ?: throw ParseException("Invalid hex char: $ch")
