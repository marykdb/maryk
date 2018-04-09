package maryk.core.extensions.bytes

/** Write the bytes of this Float to a [writer] */
internal fun Float.writeBytes(writer: (byte: Byte) -> Unit) {
    var f = this.toRawBits()
    // To make order correct
    f = (f xor (f shr 32 - 1 or Int.MIN_VALUE)) + 1
    return f.writeBytes(writer)
}

/** Reads [reader] with bytes to Float */
internal fun initFloat(reader: () -> Byte): Float {
    var f = initInt(reader) - 1
    // To make order correct
    f = f xor (f.inv() shr 32 - 1 or Int.MIN_VALUE)
    return Float.fromBits(f)
}
