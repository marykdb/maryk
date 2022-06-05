package maryk.lib.concurrency

import kotlin.native.concurrent.AtomicReference
import kotlin.native.concurrent.freeze

actual class AtomicReference<V> actual constructor(initialValue: V) {
    private val atom = AtomicReference(initialValue.freeze())

    actual fun get() = atom.value

    actual fun set(value: V) {
        atom.value = value.freeze()
    }

    actual fun compareAndSet(expected: V, new: V) = atom.compareAndSet(expected, new)
}
