package maryk.core

inline fun assert(value: Boolean, lazyMessage: () -> Any) {
    if (!value) {
        val message = lazyMessage().toString()
        throw AssertionError(message)
    }
}