package maryk.lib

val localB1 = ThreadLocal.withInitial { ByteArray(10000) }!!
val localB2 = ThreadLocal.withInitial { ByteArray(10000) }!!

actual val recyclableByteArray get() = localB1.get()!!
actual val recyclableByteArray2 get() = localB2.get()!!
