package maryk.lib

private val localB1 = object : ThreadLocal<ByteArray>() {
    override fun initialValue() = ByteArray(10_000)
}

private val localStringBuffers = object : ThreadLocal<StringDecodeBufferPool>() {
    override fun initialValue() = StringDecodeBufferPool()
}

internal actual val stringDecodeBufferPool: StringDecodeBufferPool
    get() = localStringBuffers.get()!!

actual val recyclableByteArray: ByteArray
    get() = localB1.get()!!
