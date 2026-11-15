package maryk.lib

import kotlin.native.concurrent.ThreadLocal

@ThreadLocal
internal actual val stringDecodeBufferPool = StringDecodeBufferPool()

@ThreadLocal
actual val recyclableByteArray = ByteArray(10000)
