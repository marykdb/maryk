package maryk.core.extensions

private val random = java.util.Random()

fun Byte.Companion.random() = random.nextInt().toByte()
fun Short.Companion.random() = random.nextInt().toShort()
fun Int.Companion.random() = random.nextInt()
fun Long.Companion.random() = random.nextLong()

fun Float.Companion.random() = random.nextFloat()
fun Double.Companion.random() = random.nextDouble()

fun randomBytes(size: Int): ByteArray {
    val b = ByteArray(size)
    random.nextBytes(b)
    return b
}