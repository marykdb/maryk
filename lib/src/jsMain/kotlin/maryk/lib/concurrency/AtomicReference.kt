package maryk.lib.concurrency

actual class AtomicReference<V> actual constructor(initialValue: V) {
  private var internalValue = initialValue

  actual fun compareAndSet(expected: V, new: V) =
    if (expected === internalValue) {
      internalValue = new
      true
    } else {
      false
    }

  actual fun get() = internalValue

  actual fun set(value: V) {
    internalValue = value
  }
}
