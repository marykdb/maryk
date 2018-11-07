package maryk.lib

import kotlin.native.concurrent.AtomicReference
import kotlin.native.concurrent.freeze
import kotlin.native.concurrent.isFrozen
import kotlin.native.concurrent.atomicLazy as nativeAtomicLazy

actual fun <T> safeLazy(initializer: Unit.() -> T): Lazy<T> = SafeLazyImpl(initializer)

/** An implementation for safe setting of lazy values by a Unit scoped initializer. */
internal class SafeLazyImpl<out T>(
    initializer: Unit.() -> T
) : Lazy<T> {
    private var Value: AtomicReference<Any?> = AtomicReference(UNINITIALIZED)
    private var _initializer: (Unit.() -> T)? = initializer

    override val value: T
        @Suppress("UNCHECKED_CAST")
        get() {
            var result: Any? = Value.value
            if (result !== UNINITIALIZED) {
                return result as T
            }
            result = _initializer!!(Unit).freeze()
            if (!_initializer.isFrozen) {
                _initializer = null
            }
            return if(this.Value.compareAndSet(UNINITIALIZED, result)) {
                result
            } else {
                Value.value as T
            }
        }

    /**
     * This operation on shared objects may return value which is no longer reflect the current state of lazy.
     */
    override fun isInitialized(): Boolean = (Value.value !== UNINITIALIZED)

    override fun toString(): String = if (isInitialized())
        value.toString() else "Lazy value not initialized yet."
}

internal object UNINITIALIZED {
    // So that single-threaded configs can use those as well.
    init {
        freeze()
    }
}
