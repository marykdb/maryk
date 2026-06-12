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
    var index = 0
    for (b in src) {
        val u = b.toInt() and 0xFF
        when (u) {
            0x00 -> {
                out[index++] = 0x01
                out[index++] = 0x01
            }
            0x01 -> {
                out[index++] = 0x01
                out[index++] = 0x02
            }
            else -> out[index++] = u.toByte()
        }
    }
    return out
}

internal fun ByteArray.zeroFreeEncodedLength(): Int {
    var length = 0
    for (b in this) {
        length = length.checkedZeroFreeLengthPlus(
            when ((b.toInt() and 0xFF)) {
                0x00, 0x01 -> 2
                else -> 1
            }
        )
    }
    return length
}

internal fun Int.checkedZeroFreeLengthPlus(addend: Int): Int {
    require(addend >= 0) { "Zero-free encoded length cannot be negative: $addend" }
    require(this <= Int.MAX_VALUE - addend) { "Zero-free encoded length exceeds Int range" }
    return this + addend
}

/** Decode a stream encoded by [encodeZeroFreeUsing01]. */
internal fun decodeZeroFreeUsing01(encoded: ByteArray): ByteArray {
    val out = ArrayList<Byte>(encoded.size)
    var i = 0
    while (i < encoded.size) {
        val u = encoded[i].toInt() and 0xFF
        if (u != 0x01) {
            require(u != 0x00) { "Encoded stream contains 0x00, which is disallowed" }
            out.add(encoded[i])
            i++
        } else {
            require(i + 1 < encoded.size) { "Truncated escape at end" }
            val v = encoded[i + 1].toInt() and 0xFF
            val orig = when (v) {
                0x01 -> 0x00
                0x02 -> 0x01
                else -> error("Invalid escape: 0x01 0x${v.toString(16).padStart(2, '0')}")
            }
            out.add(orig.toByte())
            i += 2
        }
    }
    return out.toByteArray()
}

internal fun decodeZeroFreeUsing01OrNull(encoded: ByteArray): ByteArray? = try {
    decodeZeroFreeUsing01(encoded)
} catch (_: IllegalArgumentException) {
    null
} catch (_: IllegalStateException) {
    null
}
