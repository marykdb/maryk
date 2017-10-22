package maryk.core.bytes;

fun initString(length: Int, reader: () -> Byte) = String(
    ByteArray(length) {
        reader()
    }
)

fun String.writeBytes(reserver: (size: Int) -> Unit, writer: (byte: Byte) -> Unit) {
    reserver(calculateUTF8Length(this))
    toUTF8Bytes(this, writer)
}

/** Calculates the length of a String in UTF8 in an optimized way
 * @param string to calculate length of
 * @throws IllegalArgumentException when string contains invalid UTF-16: unpaired surrogates
 */
private fun calculateUTF8Length(string: String): Int {
    val utf16Length = string.length
    var utf8Length = utf16Length
    var i = 0

    // Count ASCII chars.
    while (i < utf16Length && string[i].toInt() < 0x80) {
        i++
    }

    // Count other chars
    while (i < utf16Length) {
        val c = string[i]
        if (c.toInt() < 0x800) {
            // Count any chars anything below 0x800.
            utf8Length += (0x7f - c.toInt()) ushr 31
        } else {
            // Count remaining chars
            utf8Length += calculateGenericUTF8Length(string, i)
            break
        }
        i++
    }

    // Check if utf8 length did not overflow the Int to become smaller than the utf16 length
    if (utf8Length < utf16Length) {
        throw IllegalArgumentException("UTF-8 length does not fit in int: ${utf8Length + (1L shl 32)}")
    }
    return utf8Length
}

/** Calculates the length of a String in UTF8 in a less optimized way
 * @param string to calculate length of
 * @param startPosition position to start calculating length
 * @throws IllegalArgumentException when string contains invalid UTF-16: unpaired surrogates
 */
private fun calculateGenericUTF8Length(string: String, startPosition: Int): Int {
    var utf8Length = 0
    val utf16Length = string.length
    var i = startPosition
    while (i < utf16Length) {
        val char = string[i]
        when {
            char.toInt() < 0x800 ->
                utf8Length += (0x7f - char.toInt()) ushr 31
            else -> {
                utf8Length += 2
                // Check if char is a correct surrogate pair
                if (Character.isSurrogate(char)) {
                    val cp = Character.codePointAt(string, i)
                    if (cp < Character.MIN_SUPPLEMENTARY_CODE_POINT) {
                        throw IllegalArgumentException("Unpaired surrogate at index $i")
                    }
                    i++
                }
            }
        }
        i++
    }
    return utf8Length
}

/** Writes the UTF8 bytes of a String to a writer.
 * @param string to write as bytes
 * @param writer to write bytes with
 * @throws IllegalArgumentException when string contains invalid UTF-16 unpaired surrogates
 */
private fun toUTF8Bytes(string: String, writer: (byte: Byte) -> Unit) {
    val utf16Length = string.length
    var i = 0
    while (i < utf16Length) {
        val char = string[i]
        val charInt = char.toInt()
        when {
            charInt < 0x80 -> // ASCII
                writer(char.toByte())
            char.toInt() < 0x800 -> { // 11 bits, two UTF-8 bytes
                writer((0xF shl 6 or (charInt ushr 6)).toByte())
                writer((0x80 or (0x3F and charInt)).toByte())
            }
            char < Character.MIN_SURROGATE || Character.MAX_SURROGATE < char -> {
                // Max possible character is 0xFFFF. This is encoded in 3 UTF-8 bytes.
                writer((0xF shl 5 or (charInt ushr 12)).toByte())
                writer((0x80 or (0x3F and (charInt ushr 6))).toByte())
                writer((0x80 or (0x3F and charInt)).toByte())
            }
            else -> {
                val low = string[++i]
                if (i == string.length || !Character.isSurrogatePair(char, low)) {
                    throw IllegalArgumentException("Unpaired surrogate at index: ${i - 1}")
                }
                val codePoint = Character.toCodePoint(char, low)
                writer((0xF shl 4 or (codePoint ushr 18)).toByte())
                writer((0x80 or (0x3F and (codePoint ushr 12))).toByte())
                writer((0x80 or (0x3F and (codePoint ushr 6))).toByte())
                writer((0x80 or (0x3F and codePoint)).toByte())
            }
        }
        i++
    }
}
