package maryk.core.extensions.bytes

/** Write the bytes of this Double to a writer
 * @param writer to write this Double to
 */
internal fun Double.writeBytes(writer: (byte: Byte) -> Unit) {
    var l = this.toRawBits()
    l = (l xor ((l shr 64 - 1) or Long.MIN_VALUE)) + 1
    return l.writeBytes(writer)
}

/** Converts reader with bytes to Double
 * @param reader to read bytes from
 * @return Double represented by bytes
 */
internal fun initDouble(reader: () -> Byte): Double {
    var l = initLong(reader) - 1
    l = l xor (l.inv() shr 64 - 1 or java.lang.Long.MIN_VALUE)
    return Double.fromBits(l)
}