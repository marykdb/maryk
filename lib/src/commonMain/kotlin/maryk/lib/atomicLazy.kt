package maryk.lib

/** A thread safe lazy implementation for each platform */
expect fun <T> atomicLazy(initializer: () -> T): Lazy<T>
