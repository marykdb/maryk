package maryk.lib.bytes

private const val MIN_SUPPLEMENTARY_CODE_POINT = 0x010000

expect fun fromCodePoint(value: Int): String
expect fun initString(bytes: ByteArray, offset: Int, length: Int): String
expect fun initString(length: Int, reader: () -> Byte): String
expect fun codePointAt(string: String, index: Int): Int

fun String.writeUTF8Bytes(writer: (byte: Byte) -> Unit) = this.toUTF8Bytes(writer)

/**
 * Calculates the length of a String in UTF8 bytes in an optimized way
 * @throws IllegalArgumentException when string contains invalid UTF-16: unpaired surrogates
 */
fun String.calculateUTF8ByteLength(): Int {
    val utf16Length = this.length
    var utf8Length = utf16Length
    var i = 0

    // Count ASCII chars.
    while (i < utf16Length && this[i].code < 0x80) {
        i++
    }

    // Count other chars
    while (i < utf16Length) {
        val c = this[i]
        if (c.code < 0x800) {
            // Count any chars anything below 0x800.
            utf8Length += (0x7f - c.code) ushr 31
        } else {
            // Count remaining chars
            utf8Length += calculateGenericUTF8Length(this, i)
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

/**
 * Calculates the length of [string] from [startPosition] in UTF8 in a less optimized way
 * @throws IllegalArgumentException when string contains invalid UTF-16: unpaired surrogates
 */
private fun calculateGenericUTF8Length(string: String, startPosition: Int): Int {
    var utf8Length = 0
    val utf16Length = string.length
    var i = startPosition
    while (i < utf16Length) {
        val char = string[i]
        if (char.code < 0x800) {
            utf8Length += (0x7f - char.code) ushr 31
        } else {
            utf8Length += 2
            // Check if char is a correct surrogate pair
            if (isSurrogate(char)) {
                val cp = codePointAt(string, i)
                if (cp < MIN_SUPPLEMENTARY_CODE_POINT) {
                    throw IllegalArgumentException("Unpaired surrogate at index $i")
                }
                i++
            }
        }
        i++
    }
    return utf8Length
}

/**
 * Writes the UTF8 bytes of String to a [writer].
 * @throws IllegalArgumentException when string contains invalid UTF-16 unpaired surrogates
 */
private fun String.toUTF8Bytes(writer: (byte: Byte) -> Unit) {
    val utf16Length = this.length
    var i = 0
    while (i < utf16Length) {
        val char = this[i]
        val charInt = char.code
        when {
            charInt < 0x80 -> writer(char.code.toByte()) // ASCII

            char.code < 0x800 -> { // 11 bits, two UTF-8 bytes
                writer((0xF shl 6 or (charInt ushr 6)).toByte())
                writer((0x80 or (0x3F and charInt)).toByte())
            }
            char < Char.MIN_SURROGATE || Char.MAX_SURROGATE < char -> {
                // Max possible character is 0xFFFF. This is encoded in 3 UTF-8 bytes.
                writer((0xF shl 5 or (charInt ushr 12)).toByte())
                writer((0x80 or (0x3F and (charInt ushr 6))).toByte())
                writer((0x80 or (0x3F and charInt)).toByte())
            }
            else -> {
                val low = this[++i]
                if (i == this.length || !isSurrogatePair(char, low)) {
                    throw IllegalArgumentException("Unpaired surrogate at index: ${i - 1}")
                }
                val codePoint = toCodePoint(char, low)
                writer((0xF shl 4 or (codePoint ushr 18)).toByte())
                writer((0x80 or (0x3F and (codePoint ushr 12))).toByte())
                writer((0x80 or (0x3F and (codePoint ushr 6))).toByte())
                writer((0x80 or (0x3F and codePoint)).toByte())
            }
        }
        i++
    }
}

private fun toCodePoint(high: Char, low: Char) =
    (high.code shl 10) + low.code + (
            MIN_SUPPLEMENTARY_CODE_POINT
                    - (Char.MIN_HIGH_SURROGATE.code shl 10)
                    - Char.MIN_LOW_SURROGATE.code
            )

private fun isSurrogatePair(high: Char, low: Char) =
    isHighSurrogate(high) && isLowSurrogate(low)

private fun isHighSurrogate(ch: Char) =
    ch >= Char.MIN_HIGH_SURROGATE && ch.code < Char.MAX_HIGH_SURROGATE.code + 1

private fun isLowSurrogate(ch: Char) =
    ch >= Char.MIN_LOW_SURROGATE && ch.code < Char.MAX_LOW_SURROGATE.code + 1

private fun isSurrogate(ch: Char) =
    ch >= Char.MIN_SURROGATE && ch.code < Char.MAX_SURROGATE.code + 1
