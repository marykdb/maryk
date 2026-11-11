package maryk.datastore.foundationdb

import kotlinx.atomicfu.atomic
import maryk.core.properties.types.Bytes

/** Bounded best-effort cleanup keys. Receipt keys are unique per logical request; repeated
 * clears after a failed cleanup are harmless. Only immutable nodes are published. */
internal class PendingReceiptQueue(private val capacity: Int = 65_536) {
    private class Node(val key: Bytes, val next: Node?)
    private class Snapshot(val head: Node?, val size: Int)
    private val empty = Snapshot(null, 0)
    private val state = atomic(empty)

    init { require(capacity > 0) }

    fun offer(key: ByteArray): Boolean {
        var ownedKey: Bytes? = null
        while (true) {
            val previous = state.value
            if (previous.size >= capacity) return false
            val value = ownedKey ?: Bytes(key).also { ownedKey = it }
            if (state.compareAndSet(previous, Snapshot(Node(value, previous.head), previous.size + 1))) return true
        }
    }

    fun drain(): List<Bytes> {
        val batch = state.getAndSet(empty)
        if (batch.size == 0) return emptyList()
        val keys = ArrayList<Bytes>(batch.size)
        var node = batch.head
        while (node != null) { keys += node.key; node = node.next }
        return keys
    }

    /** Cleanup failure is rare: preserve the older batch first and deduplicate replayed keys. */
    fun restore(older: List<Bytes>) {
        while (true) {
            val previous = state.value
            val keys = LinkedHashSet<Bytes>()
            for (key in older) { if (keys.size == capacity) break; keys += key }
            var node = previous.head
            while (node != null && keys.size < capacity) { keys += node.key; node = node.next }
            var head: Node? = null
            for (key in keys) head = Node(key, head)
            if (state.compareAndSet(previous, Snapshot(head, keys.size))) return
        }
    }
}
