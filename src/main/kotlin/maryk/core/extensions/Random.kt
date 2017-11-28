package maryk.core.extensions

expect fun Byte.Companion.random(): Byte
expect fun Short.Companion.random(): Short
expect fun Int.Companion.random(): Int
expect fun Long.Companion.random(): Long

expect fun Float.Companion.random(): Float
expect fun Double.Companion.random(): Double

expect fun randomBytes(size: Int): ByteArray