package maryk.core.extensions

/** Create random Byte value */
expect fun Byte.Companion.random(): Byte
/** Create random Short value */
expect fun Short.Companion.random(): Short
/** Create random Int value */
expect fun Int.Companion.random(): Int
/** Create random Long value */
expect fun Long.Companion.random(): Long

/** Create random Float value */
expect fun Float.Companion.random(): Float
/** Create random Double value */
expect fun Double.Companion.random(): Double

/** Create ByteArray of [size] with random values */
expect fun randomBytes(size: Int): ByteArray