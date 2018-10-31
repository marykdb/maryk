package maryk.lib

actual fun <T> atomicLazy(initializer: () -> T): Lazy<T> = lazy(initializer)
