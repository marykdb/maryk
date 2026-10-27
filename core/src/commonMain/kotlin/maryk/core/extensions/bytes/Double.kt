package maryk.core.extensions.bytes

/** Lossless, numerically ordered storage bytes (raw NaN payloads are retained). */
internal fun Double.writeBytes(writer: (byte: Byte) -> Unit) {
    val bits = toRawBits()
    val ordered = if (bits < 0) bits.inv() else bits xor Long.MIN_VALUE
    ordered.toULong().writeBytes(writer)
}

/** Decode the common value/key representation. */
internal fun initDouble(reader: () -> Byte): Double {
    val ordered = initULong(reader = reader).toLong()
    val bits = if (ordered < 0) ordered xor Long.MIN_VALUE else ordered.inv()
    return Double.fromBits(bits)
}

/** Write the bytes of this Double in little endian order to a [writer] */
internal fun Double.writeTransportableBytes(writer: (byte: Byte) -> Unit) =
    this.toRawBits().writeLittleEndianBytes(writer)

/** Converts [reader] with little endian encoded bytes to Double */
internal fun initDoubleFromTransport(reader: () -> Byte) =
    Double.fromBits(
        initLongLittleEndian(reader)
    )

/** Index comparisons treat all NaNs alike; all other values use the ordinary codec. */
internal fun Double.writeOrderedBytes(writer: (Byte) -> Unit) =
    Double.fromBits(toBits()).writeBytes(writer)
