package maryk.lib

/** Short term recyclable byte array which is thread local */
expect val recyclableByteArray: ByteArray

/** One bounded scratch buffer per thread; nested decoders use an owned fallback. */
internal class StringDecodeBufferPool {
    val bytes = ByteArray(10_000)
    var inUse = false
}

internal expect val stringDecodeBufferPool: StringDecodeBufferPool
