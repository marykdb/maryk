package maryk.lib

import kotlin.native.concurrent.AtomicReference
import kotlin.native.concurrent.freeze
import kotlin.native.concurrent.isFrozen

actual fun <T> safeLazy(initializer: Unit.() -> T): Lazy<T> = SafeLazyImpl(initializer)

/** An implementation for safe setting of lazy values by a Unit scoped initializer. */
internal class SafeLazyImpl<out T>(
    initializer: Unit.() -> T
) : Lazy<T> {
    private var _value: AtomicReference<Any?> = AtomicReference(UNINITIALIZED)
    private var _initializer: (Unit.() -> T)? = initializer

    override val value: T
        @Suppress("UNCHECKED_CAST")
        get() {
            var result: Any? = _value.value
            if (result !== UNINITIALIZED) {
                return result as T
            }
            result = _initializer!!(Unit).freeze()
            if (!_initializer.isFrozen) {
                _initializer = null
            }
            return if (this._value.compareAndSet(UNINITIALIZED, result)) {
                result
            } else {
                _value.value as T
            }
        }

    /**
     * This operation on shared objects may return value which is no longer reflect the current state of lazy.
     */
    override fun isInitialized(): Boolean = (_value.value !== UNINITIALIZED)

    override fun toString(): String = if (isInitialized())
        value.toString() else "Lazy value not initialized yet."
}

internal object UNINITIALIZED {
    // So that single-threaded configs can use those as well.
    init {
        freeze()
    }
}
