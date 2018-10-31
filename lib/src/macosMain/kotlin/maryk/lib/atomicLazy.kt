package maryk.lib

import kotlin.native.concurrent.atomicLazy as nativeAtomicLazy

actual fun <T> atomicLazy(initializer: () -> T): Lazy<T> = nativeAtomicLazy(initializer)
