package maryk.lib.concurrency

/** AtomicReference for multi-platform usage */
expect class AtomicReference<V>(initialValue: V) {
    fun set(value: V)
    fun get(): V
    fun compareAndSet(expected: V, new: V): Boolean
}
