package maryk.lib.bytes

import maryk.lib.recyclableByteArray

private const val MIN_SUPPLEMENTARY_CODE_POINT = 0x010000

private const val SURROGATE_OFFSET = MIN_SUPPLEMENTARY_CODE_POINT - (Char.MIN_HIGH_SURROGATE.code shl 10) - Char.MIN_LOW_SURROGATE.code
private const val MAX_CODE_POINT = 0x10FFFF

fun fromCodePoint(value: Int): String {
    require(value in 0..MAX_CODE_POINT) { "Invalid Unicode code point: $value" }

    return if (value < MIN_SUPPLEMENTARY_CODE_POINT) {
        charArrayOf(value.toChar()).concatToString()
    } else {
        val adjusted = value - MIN_SUPPLEMENTARY_CODE_POINT
        val high = (Char.MIN_HIGH_SURROGATE.code + (adjusted ushr 10)).toChar()
        val low = (Char.MIN_LOW_SURROGATE.code + (adjusted and 0x3FF)).toChar()
        charArrayOf(high, low).concatToString()
    }
}

fun initString(length: Int, reader: () -> Byte): String =
    if (length > recyclableByteArray.size) {
        ByteArray(length) {
            reader()
        }.decodeToString()
    } else {
        for (index in 0 until length) {
            recyclableByteArray[index] = reader()
        }
        recyclableByteArray.decodeToString(0, length)
    }

/**
 * Calculates the length of a String in UTF8 bytes in an optimized way
 * @throws IllegalArgumentException when string contains invalid UTF-16 surrogates or the UTF-8 length exceeds Int.MAX_VALUE
 */
fun String.calculateUTF8ByteLength(): Int {
    var utf8Length = 0
    var i = 0
    while (i < length) {
        val c = this[i]
        when {
            c.code < 0x80 -> utf8Length++
            c.code < 0x800 -> utf8Length += 2
            c.isHighSurrogate() -> {
                if (i + 1 >= length || !this[i + 1].isLowSurrogate()) {
                    throw IllegalArgumentException("Unpaired surrogate at index $i")
                }
                utf8Length += 4
                i++
            }
            c.isLowSurrogate() -> throw IllegalArgumentException("Unexpected low surrogate at index $i")
            else -> utf8Length += 3
        }
        i++
    }

    if (utf8Length < 0) {
        throw IllegalArgumentException("UTF-8 length overflow: ${utf8Length.toLong() and 0xFFFFFFFFL}")
    }
    return utf8Length
}

/**
 * Writes the UTF8 bytes of String to a [writer].
 * @throws IllegalArgumentException when string contains invalid UTF-16 unpaired surrogates
 */
fun String.writeUTF8Bytes(writer: (byte: Byte) -> Unit) {
    var i = 0
    while (i < length) {
        val char = this[i]
        when {
            char.code < 0x80 -> writer(char.code.toByte())
            char.code < 0x800 -> {
                writer((0xC0 or (char.code ushr 6)).toByte())
                writer((0x80 or (0x3F and char.code)).toByte())
            }
            char < Char.MIN_SURROGATE || char > Char.MAX_SURROGATE -> {
                writer((0xE0 or (char.code ushr 12)).toByte())
                writer((0x80 or (0x3F and (char.code ushr 6))).toByte())
                writer((0x80 or (0x3F and char.code)).toByte())
            }
            else -> {
                if (!char.isHighSurrogate() || i + 1 >= this.length) {
                    throw IllegalArgumentException("Unpaired surrogate at index: $i")
                }
                val low = this[++i]
                if (!low.isLowSurrogate()) {
                    throw IllegalArgumentException("Unpaired surrogate at index: ${i - 1}")
                }

                val codePoint = (char.code shl 10) + low.code + SURROGATE_OFFSET
                writer((0xF0 or (codePoint ushr 18)).toByte())
                writer((0x80 or (0x3F and (codePoint ushr 12))).toByte())
                writer((0x80 or (0x3F and (codePoint ushr 6))).toByte())
                writer((0x80 or (0x3F and codePoint)).toByte())
            }
        }
        i++
    }
}
