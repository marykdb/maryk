package maryk.core.extensions.bytes

/** Write the bytes of this Double to a [writer] */
internal fun Double.writeBytes(writer: (byte: Byte) -> Unit) {
    var l = this.toRawBits()
    // To make order correct
    l = (l xor ((l shr 64 - 1) or Long.MIN_VALUE)) + 1
    return l.writeBytes(writer)
}

/** Converts [reader] with bytes to Double */
internal fun initDouble(reader: () -> Byte): Double {
    var l = initLong(reader) - 1
    // To make order correct
    l = l xor (l.inv() shr 64 - 1 or Long.MIN_VALUE)
    return Double.fromBits(l)
}

/** Write the bytes of this Double in little endian order to a [writer] */
internal fun Double.writeTransportableBytes(writer: (byte: Byte) -> Unit) =
    this.toRawBits().writeLittleEndianBytes(writer)

/** Converts [reader] with little endian encoded bytes to Double */
internal fun initDoubleFromTransport(reader: () -> Byte) =
    Double.fromBits(
        initLongLittleEndian(reader)
    )
