package maryk.lib

val localB1 = ThreadLocal.withInitial { ByteArray(10000) }!!

actual val recyclableByteArray get() = localB1.get()!!
