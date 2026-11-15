package maryk.lib.bytes

import maryk.lib.stringDecodeBufferPool

private const val INITIAL_STRING_BUFFER_SIZE = 10_000

private const val MIN_SUPPLEMENTARY_CODE_POINT = 0x010000

private const val SURROGATE_OFFSET = MIN_SUPPLEMENTARY_CODE_POINT - (Char.MIN_HIGH_SURROGATE.code shl 10) - Char.MIN_LOW_SURROGATE.code
private const val MAX_CODE_POINT = 0x10FFFF

fun fromCodePoint(value: Int): String {
    require(value in 0..MAX_CODE_POINT) { "Invalid Unicode code point: $value" }
    require(value !in Char.MIN_SURROGATE.code..Char.MAX_SURROGATE.code) {
        "Invalid Unicode surrogate code point: $value"
    }

    return if (value < MIN_SUPPLEMENTARY_CODE_POINT) {
        charArrayOf(value.toChar()).concatToString()
    } else {
        val adjusted = value - MIN_SUPPLEMENTARY_CODE_POINT
        val high = (Char.MIN_HIGH_SURROGATE.code + (adjusted ushr 10)).toChar()
        val low = (Char.MIN_LOW_SURROGATE.code + (adjusted and 0x3FF)).toChar()
        charArrayOf(high, low).concatToString()
    }
}

fun initString(length: Int, reader: () -> Byte): String {
    require(length >= 0) { "String byte length cannot be negative: $length" }

    if (length == 0) return ""
    val pool = stringDecodeBufferPool
    if (length <= pool.bytes.size && !pool.inUse) {
        pool.inUse = true
        try {
            for (index in 0 until length) pool.bytes[index] = reader()
            return pool.bytes.decodeUtf8Strict(0, length)
        } finally {
            pool.inUse = false
        }
    }
    // A nested supplier must not overwrite a leased buffer. Large inputs still
    // grow incrementally so a bogus declared length cannot force a huge allocation.
    var bytes = ByteArray(minOf(length, INITIAL_STRING_BUFFER_SIZE))
    var index = 0
    while (index < length) {
        if (index == bytes.size) {
            val remaining = length - bytes.size
            bytes = bytes.copyOf(bytes.size + minOf(bytes.size, remaining))
        }
        bytes[index++] = reader()
    }
    return bytes.decodeUtf8Strict(0, length)
}

/** Decode a bounded UTF-8 slice, avoiding a strict decoder for provably valid ASCII. */
fun ByteArray.decodeUtf8Strict(startIndex: Int = 0, endIndex: Int = size): String {
    require(startIndex >= 0 && endIndex >= startIndex && endIndex <= size)
    return decodeUtf8StrictPlatform(startIndex, endIndex)
}

internal expect fun ByteArray.decodeUtf8StrictPlatform(startIndex: Int, endIndex: Int): String

internal fun ByteArray.decodeUtf8StrictFallback(startIndex: Int, endIndex: Int): String {
    var index = startIndex
    while (index <= endIndex - 8) {
        val first = this[index].toInt() or this[index + 1].toInt() or this[index + 2].toInt() or this[index + 3].toInt()
        val second = this[index + 4].toInt() or this[index + 5].toInt() or this[index + 6].toInt() or this[index + 7].toInt()
        if ((first or second) < 0) return decodeToString(startIndex, endIndex, throwOnInvalidSequence = true)
        index += 8
    }
    while (index < endIndex) {
        if (this[index] < 0) return decodeToString(startIndex, endIndex, throwOnInvalidSequence = true)
        index++
    }
    return decodeToString(startIndex, endIndex)
}

/**
 * Calculates the length of a String in UTF8 bytes in an optimized way
 * @throws IllegalArgumentException when string contains invalid UTF-16 surrogates or the UTF-8 length exceeds Int.MAX_VALUE
 */
fun String.calculateUTF8ByteLength(): Int {
    // At most three UTF-8 bytes per UTF-16 code unit. Keep ordinary strings on
    // the Int-only path; oversized inputs need a non-wrapping accumulator.
    if (length > Int.MAX_VALUE / 3) return calculateUTF8ByteLengthChecked()
    var i = 0
    // Skip ASCII in groups; the scalar path still validates every non-ASCII sequence.
    while (i <= length - 8) {
        val first = this[i].code or this[i + 1].code or this[i + 2].code or this[i + 3].code
        val second = this[i + 4].code or this[i + 5].code or this[i + 6].code or this[i + 7].code
        if ((first or second) >= 0x80) break
        i += 8
    }
    while (i <= length - 4) {
        if ((this[i].code or this[i + 1].code or this[i + 2].code or this[i + 3].code) >= 0x80) break
        i += 4
    }
    var utf8Length = i
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

/** Checked slow path; the explicit budget also permits bounded tests without giant allocations. */
internal fun CharSequence.calculateUTF8ByteLengthChecked(maxByteLength: Int = Int.MAX_VALUE): Int {
    require(maxByteLength >= 0)
    var size = 0L
    var index = 0
    while (index < length) {
        val char = this[index]
        size += when {
            char.code < 0x80 -> 1
            char.code < 0x800 -> 2
            char.isHighSurrogate() -> {
                require(index + 1 < length && this[index + 1].isLowSurrogate()) { "Unpaired surrogate at index $index" }
                index++
                4
            }
            char.isLowSurrogate() -> throw IllegalArgumentException("Unexpected low surrogate at index $index")
            else -> 3
        }
        require(size <= maxByteLength.toLong()) { "UTF-8 length overflow: $size" }
        index++
    }
    return size.toInt()
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
