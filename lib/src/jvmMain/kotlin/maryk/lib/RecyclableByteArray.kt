package maryk.lib

private val localB1 = object : ThreadLocal<ByteArray>() {
    override fun initialValue() = ByteArray(10_000)
}

actual val recyclableByteArray: ByteArray
    get() = localB1.get()!!
