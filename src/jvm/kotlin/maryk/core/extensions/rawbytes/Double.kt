package maryk.core.extensions.rawbytes

/**
 * Converts Double to Long representation
 * @return double as long bits
 */
fun Double.toRawLongBits() = java.lang.Double.doubleToRawLongBits(this)

/**
 * Converts byte array to Double
 * @param value to convert
 * @return Double represented by bytes
 */
fun initDouble(value: Long) = java.lang.Double.longBitsToDouble(value)
