package maryk.datastore.foundationdb.processors.helpers

/**
 * Encode a byte array so it contains no 0x00 bytes by escaping using 0x01.
 * 0x00 -> 0x01 0x01
 * 0x01 -> 0x01 0x02
 * others unchanged.
 */
internal fun encodeZeroFreeUsing01(src: ByteArray): ByteArray {
    val out = ArrayList<Byte>(src.size + src.size / 4)
    for (b in src) {
        val u = b.toInt() and 0xFF
        when (u) {
            0x00 -> { out.add(0x01); out.add(0x01) }
            0x01 -> { out.add(0x01); out.add(0x02) }
            else -> out.add(u.toByte())
        }
    }
    return out.toByteArray()
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
