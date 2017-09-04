package maryk.core.extensions.rawbytes

/**
 * Converts Float to int representation
 * @return float as int bits
 */
fun Float.toRawIntBits() = java.lang.Float.floatToRawIntBits(this)

/**
 * Converts byte array to Float
 * @param value  to convert
 * @return Float represented by bytes
 */
fun initFloat(value: Int) = java.lang.Float.intBitsToFloat(value)
