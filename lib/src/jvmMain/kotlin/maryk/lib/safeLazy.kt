package maryk.lib

actual fun <T> safeLazy(initializer: Unit.() -> T): Lazy<T> = lazy { initializer(Unit) }
