package maryk.core.extensions.bytes

/** Write the bytes of this Double to a writer
 * @param writer to write this Double to
 */
internal fun Float.writeBytes(writer: (byte: Byte) -> Unit) {
    var f = this.toRawBits()
    f = (f xor (f shr 32 - 1 or Integer.MIN_VALUE)) + 1
    return f.writeBytes(writer)
}

/** Converts reader with bytes to Double
 * @param reader to read bytes from
 * @return Double represented by bytes
 */
internal fun initFloat(reader: () -> Byte): Float {
    var f = initInt(reader) - 1
    f = f xor (f.inv() shr 32 - 1 or Integer.MIN_VALUE)
    return Float.fromBits(f)
}