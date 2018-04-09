package maryk.lib.extensions

private val random = java.util.Random()

actual fun Byte.Companion.random() = random.nextInt().toByte()
actual fun Short.Companion.random() = random.nextInt().toShort()
actual fun Int.Companion.random() = random.nextInt()
actual fun Long.Companion.random() = random.nextLong()

actual fun Float.Companion.random() = random.nextFloat()
actual fun Double.Companion.random() = random.nextDouble()

actual fun randomBytes(size: Int): ByteArray {
    val b = ByteArray(size)
    random.nextBytes(b)
    return b
}
