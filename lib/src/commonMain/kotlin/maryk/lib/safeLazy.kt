package maryk.lib

/** A thread safe lazy implementation for each platform */
expect fun <T> safeLazy(initializer: Unit.() -> T): Lazy<T>
