package maryk.lib

import kotlin.native.concurrent.ThreadLocal

@ThreadLocal
actual val recyclableByteArray = ByteArray(10000)
