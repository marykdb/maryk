package maryk.core.bytes

private const val MIN_LOW_SURROGATE = '\uDC00'
private const val MIN_HIGH_SURROGATE = '\uD800'
private const val MAX_LOW_SURROGATE = '\uDFFF'
private const val MAX_HIGH_SURROGATE = '\uDBFF'

private const val MIN_SURROGATE = MIN_HIGH_SURROGATE
private const val MAX_SURROGATE = MAX_LOW_SURROGATE

private const val MIN_SUPPLEMENTARY_CODE_POINT = 0x010000

expect fun initString(length: Int, reader: () -> Byte): String

expect fun String.charPointAt(index: Int) : Int

fun String.writeUTF8Bytes(writer: (byte: Byte) -> Unit) = this.toUTF8Bytes(writer)

/** Calculates the length of a String in UTF8 bytes in an optimized way
 * @throws IllegalArgumentException when string contains invalid UTF-16: unpaired surrogates
 */
fun String.calculateUTF8ByteLength(): Int {
    val utf16Length = this.length
    var utf8Length = utf16Length
    var i = 0

    // Count ASCII chars.
    while (i < utf16Length && this[i].toInt() < 0x80) {
        i++
    }

    // Count other chars
    while (i < utf16Length) {
        val c = this[i]
        if (c.toInt() < 0x800) {
            // Count any chars anything below 0x800.
            utf8Length += (0x7f - c.toInt()) ushr 31
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
                if (isSurrogate(char)) {
                    val cp = string.charPointAt(i)
                    if (cp < MIN_SUPPLEMENTARY_CODE_POINT) {
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
 * @param writer to write bytes with
 * @throws IllegalArgumentException when string contains invalid UTF-16 unpaired surrogates
 */
private fun String.toUTF8Bytes(writer: (byte: Byte) -> Unit) {
    val utf16Length = this.length
    var i = 0
    while (i < utf16Length) {
        val char = this[i]
        val charInt = char.toInt()
        when {
            charInt < 0x80 -> // ASCII
                writer(char.toByte())
            char.toInt() < 0x800 -> { // 11 bits, two UTF-8 bytes
                writer((0xF shl 6 or (charInt ushr 6)).toByte())
                writer((0x80 or (0x3F and charInt)).toByte())
            }
            char < MIN_SURROGATE || MAX_SURROGATE < char -> {
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

private fun toCodePoint(high: Char, low: Char)
        = (high.toInt() shl 10) + low.toInt() + (MIN_SUPPLEMENTARY_CODE_POINT
            - (MIN_HIGH_SURROGATE.toInt() shl 10)
            - MIN_LOW_SURROGATE.toInt())

private fun isSurrogatePair(high: Char, low: Char)
        = isHighSurrogate(high) && isLowSurrogate(low)

private fun isHighSurrogate(ch: Char)
        = ch >= MIN_HIGH_SURROGATE && ch.toInt() < MAX_HIGH_SURROGATE.toInt() + 1

private fun isLowSurrogate(ch: Char)
        = ch >= MIN_LOW_SURROGATE && ch.toInt() < MAX_LOW_SURROGATE.toInt() + 1

private fun isSurrogate(ch: Char)
        = ch >= MIN_SURROGATE && ch.toInt() < MAX_SURROGATE.toInt() + 1