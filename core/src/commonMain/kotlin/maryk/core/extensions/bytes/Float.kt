package maryk.core.extensions.bytes

/** Lossless, numerically ordered storage bytes (raw NaN payloads are retained). */
internal fun Float.writeBytes(writer: (byte: Byte) -> Unit) {
    val bits = toRawBits()
    val ordered = if (bits < 0) bits.inv() else bits xor Int.MIN_VALUE
    ordered.toUInt().writeBytes(writer)
}

/** Decode the common value/key representation. */
internal fun initFloat(reader: () -> Byte): Float {
    val ordered = initUInt(reader = reader).toInt()
    val bits = if (ordered < 0) ordered xor Int.MIN_VALUE else ordered.inv()
    return Float.fromBits(bits)
}

/** Write the bytes of this Float to a [writer] in little endian order */
internal fun Float.writeTransportBytes(writer: (byte: Byte) -> Unit) {
    return this.toRawBits().writeLittleEndianBytes(writer)
}

/** Reads [reader] with little endian ordered bytes to Float */
internal fun initFloatFromTransport(reader: () -> Byte) =
    Float.fromBits(
        initIntLittleEndian(reader)
    )

/** Index comparisons treat all NaNs alike; all other values use the ordinary codec. */
internal fun Float.writeOrderedBytes(writer: (Byte) -> Unit) =
    Float.fromBits(toBits()).writeBytes(writer)
