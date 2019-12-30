package maryk.lib

@ThreadLocal
actual val recyclableByteArray = ByteArray(10000)

@ThreadLocal
actual val recyclableByteArray2 = ByteArray(10000)
