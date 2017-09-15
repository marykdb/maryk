package maryk.core.extensions.bytes

import maryk.core.extensions.rawbytes.toRawIntBits

/**
 * Converts Float to byte array
 * @param bytes    to add value to
 * @param offset to add value to
 * @return byte array
 */
internal fun Float.toBytes(bytes: ByteArray? = null, offset: Int = 0): ByteArray {
    var f = this.toRawIntBits()
    f = (f xor (f shr 32 - 1 or Integer.MIN_VALUE)) + 1
    return f.toBytes(bytes, offset)
}

/**
 * Converts byte array to Float
 * @param bytes  to convertFromBytes
 * @param offset of byte to start
 * @return Float represented by bytes
 */
internal fun initFloat(bytes: ByteArray, offset: Int = 0): Float {
    var f = initInt(bytes, offset) - 1
    f = f xor (f.inv() shr 32 - 1 or Integer.MIN_VALUE)
    return maryk.core.extensions.rawbytes.initFloat(f)
}

/** Write the bytes of this Double to a writer
 * @param writer to write this Double to
 */
internal fun Float.writeBytes(writer: (byte: Byte) -> Unit) {
    var f = this.toRawIntBits()
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
    return maryk.core.extensions.rawbytes.initFloat(f)
}