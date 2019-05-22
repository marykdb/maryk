package maryk.lib

actual fun <T> safeLazy(initializer: Unit.() -> T) = lazy { initializer(Unit) }
