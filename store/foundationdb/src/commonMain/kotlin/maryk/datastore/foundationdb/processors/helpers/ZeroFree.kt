package maryk.datastore.foundationdb.processors.helpers

/**
 * Encode a byte array so it contains no 0x00 bytes by escaping using 0x01.
 * 0x00 -> 0x01 0x01
 * 0x01 -> 0x01 0x02
 * others unchanged.
 */
internal fun encodeZeroFreeUsing01(src: ByteArray): ByteArray {
    val encodedLength = src.zeroFreeEncodedLength()
    val out = ByteArray(encodedLength)
    encodeZeroFreeUsing01Into(src, 0, src.size, out, 0)
    return out
}

internal fun encodeZeroFreeUsing01(src: ByteArray, offset: Int, length: Int): ByteArray {
    val encodedLength = src.zeroFreeEncodedLength(offset, length)
    val out = ByteArray(encodedLength)
    encodeZeroFreeUsing01Into(src, offset, length, out, 0)
    return out
}

internal fun encodeZeroFreeSuffixUsing01(src: ByteArray, prefixLength: Int): ByteArray {
    require(prefixLength in 0..src.size) { "Prefix length $prefixLength out of bounds for ${src.size}" }
    if (prefixLength == src.size) return src

    val encodedLength = prefixLength + src.zeroFreeEncodedLength(prefixLength, src.size - prefixLength)
    val out = ByteArray(encodedLength)
    src.copyInto(out, 0, 0, prefixLength)
    encodeZeroFreeUsing01Into(src, prefixLength, src.size - prefixLength, out, prefixLength)
    return out
}

private fun encodeZeroFreeUsing01Into(
    src: ByteArray,
    offset: Int,
    length: Int,
    out: ByteArray,
    outOffset: Int
) {
    var index = 0
    val end = offset + length
    for (srcIndex in offset until end) {
        val u = src[srcIndex].toInt() and 0xFF
        when (u) {
            0x00 -> {
                out[outOffset + index++] = 0x01
                out[outOffset + index++] = 0x01
            }
            0x01 -> {
                out[outOffset + index++] = 0x01
                out[outOffset + index++] = 0x02
            }
            else -> out[outOffset + index++] = u.toByte()
        }
    }
}

internal fun ByteArray.zeroFreeEncodedLength(): Int {
    return zeroFreeEncodedLength(0, size)
}

internal fun ByteArray.zeroFreeEncodedLength(offset: Int, length: Int): Int {
    require(offset >= 0) { "Offset cannot be negative: $offset" }
    require(length >= 0) { "Length cannot be negative: $length" }
    require(offset + length <= size) { "Range [$offset, ${offset + length}) out of bounds for $size" }

    var resultLength = 0
    val end = offset + length
    for (index in offset until end) {
        val b = this[index]
        resultLength = resultLength.checkedZeroFreeLengthPlus(
            when ((b.toInt() and 0xFF)) {
                0x00, 0x01 -> 2
                else -> 1
            }
        )
    }
    return resultLength
}

internal fun Int.checkedZeroFreeLengthPlus(addend: Int): Int {
    require(addend >= 0) { "Zero-free encoded length cannot be negative: $addend" }
    require(this <= Int.MAX_VALUE - addend) { "Zero-free encoded length exceeds Int range" }
    return this + addend
}

/** Decode a stream encoded by [encodeZeroFreeUsing01]. */
internal fun decodeZeroFreeUsing01(encoded: ByteArray): ByteArray {
    return decodeZeroFreeUsing01(encoded, 0, encoded.size)
}

internal fun decodeZeroFreeUsing01(encoded: ByteArray, offset: Int, length: Int): ByteArray {
    val decodedLength = encoded.zeroFreeDecodedLength(offset, length)
    val out = ByteArray(decodedLength)
    val end = offset + length
    var i = offset
    var writeIndex = 0
    while (i < end) {
        val u = encoded[i].toInt() and 0xFF
        if (u != 0x01) {
            require(u != 0x00) { "Encoded stream contains 0x00, which is disallowed" }
            out[writeIndex++] = encoded[i]
            i++
        } else {
            require(i + 1 < end) { "Truncated escape at end" }
            val v = encoded[i + 1].toInt() and 0xFF
            val orig = when (v) {
                0x01 -> 0x00
                0x02 -> 0x01
                else -> error("Invalid escape: 0x01 0x${v.toString(16).padStart(2, '0')}")
            }
            out[writeIndex++] = orig.toByte()
            i += 2
        }
    }
    return out
}

internal fun decodeZeroFreeUsing01OrNull(encoded: ByteArray): ByteArray? = try {
    decodeZeroFreeUsing01(encoded)
} catch (_: IllegalArgumentException) {
    null
} catch (_: IllegalStateException) {
    null
}

internal fun decodeZeroFreeUsing01OrNull(encoded: ByteArray, offset: Int, length: Int): ByteArray? = try {
    decodeZeroFreeUsing01(encoded, offset, length)
} catch (_: IllegalArgumentException) {
    null
} catch (_: IllegalStateException) {
    null
}

private fun ByteArray.zeroFreeDecodedLength(offset: Int, length: Int): Int {
    require(offset >= 0) { "Offset cannot be negative: $offset" }
    require(length >= 0) { "Length cannot be negative: $length" }
    require(offset + length <= size) { "Range [$offset, ${offset + length}) out of bounds for $size" }

    val end = offset + length
    var index = offset
    var decodedLength = 0
    while (index < end) {
        val value = this[index].toInt() and 0xFF
        when (value) {
            0x00 -> throw IllegalArgumentException("Encoded stream contains 0x00, which is disallowed")
            0x01 -> {
                if (index + 1 >= end) throw IllegalArgumentException("Truncated escape at end")
                when (this[index + 1].toInt() and 0xFF) {
                    0x01, 0x02 -> {
                        decodedLength++
                        index += 2
                    }
                    else -> throw IllegalStateException("Invalid escape sequence")
                }
            }
            else -> {
                decodedLength++
                index++
            }
        }
    }
    return decodedLength
}
