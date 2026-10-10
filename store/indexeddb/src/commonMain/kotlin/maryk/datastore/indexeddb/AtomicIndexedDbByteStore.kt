package maryk.datastore.indexeddb

import maryk.datastore.shared.rejectAtomic
import maryk.datastore.shared.MAX_ATOMIC_BYTES
import maryk.datastore.shared.currentAtomicContext
import maryk.lib.extensions.compare.compareTo

/** Explicit opt-in: writeBatch must use one native transaction and transaction must fence all Maryk writers. */
interface AtomicIndexedDbByteStore : IndexedDbByteStore

/** Coroutine-local routing keeps background readers out of uncommitted staging state. */
internal class AtomicAwareIndexedDbByteStore(
    private val delegate: IndexedDbByteStore,
    private val owner: Any,
) : IndexedDbByteStore by delegate {
    private suspend fun active() = currentAtomicContext(owner)?.transaction as? StagedIndexedDbByteStore ?: delegate
    override suspend fun <T> transaction(storeNames: Set<String>, mode: IndexedDbTransactionMode, block: suspend (IndexedDbByteStore) -> T): T =
        if (active() !== delegate) block(this) else delegate.transaction(storeNames, mode) { block(this) }
    override suspend fun get(storeName: String, key: ByteArray) = active().get(storeName, key)
    override suspend fun put(storeName: String, key: ByteArray, value: ByteArray) = active().put(storeName, key, value)
    override suspend fun delete(storeName: String, key: ByteArray) = active().delete(storeName, key)
    override suspend fun writeBatch(operations: List<IndexedDbWriteOperation>) = active().writeBatch(operations)
    override suspend fun scan(storeName: String, startKey: ByteArray?, includeStart: Boolean, endKey: ByteArray?, includeEnd: Boolean, reverse: Boolean, limit: UInt) =
        active().scan(storeName, startKey, includeStart, endKey, includeEnd, reverse, limit)
}

internal class StagedIndexedDbByteStore(private val delegate: IndexedDbByteStore) : IndexedDbByteStore by delegate {
    val operations = mutableListOf<IndexedDbWriteOperation>()
    var journalKey: ByteArray? = null
    private var workBytes = 0L
    private fun budget(bytes: Int) {
        workBytes += bytes
        if (workBytes > 4L * MAX_ATOMIC_BYTES) rejectAtomic("IndexedDB atomic staging exceeds 4 MiB")
    }
    override suspend fun get(storeName: String, key: ByteArray): ByteArray? {
        val operation = operations.lastOrNull { it.storeName == storeName && it.key.contentEquals(key) }
        return when (operation) {
            is IndexedDbWriteOperation.Put -> operation.value
            is IndexedDbWriteOperation.Delete -> null
            null -> delegate.get(storeName, key)?.also { budget(it.size + key.size) }
        }
    }
    override suspend fun put(storeName: String, key: ByteArray, value: ByteArray) =
        writeBatch(listOf(IndexedDbWriteOperation.Put(storeName, key, value)))
    override suspend fun delete(storeName: String, key: ByteArray) =
        writeBatch(listOf(IndexedDbWriteOperation.Delete(storeName, key)))
    override suspend fun writeBatch(operations: List<IndexedDbWriteOperation>) {
        operations.forEach { budget(it.key.size + ((it as? IndexedDbWriteOperation.Put)?.value?.size ?: 0)) }
        this.operations.addAll(operations)
    }
    override suspend fun scan(storeName: String, startKey: ByteArray?, includeStart: Boolean, endKey: ByteArray?, includeEnd: Boolean, reverse: Boolean, limit: UInt): List<Pair<ByteArray, ByteArray>> {
        fun inRange(key: ByteArray) =
            (startKey == null || key > startKey || includeStart && key.contentEquals(startKey)) &&
                (endKey == null || key < endKey || includeEnd && key.contentEquals(endKey))
        val rows = delegate.scan(storeName, startKey, includeStart, endKey, includeEnd, false, 100_001u).toMutableList()
        if (rows.size > 100_000) rejectAtomic("IndexedDB atomic scan exceeds 100000 rows")
        rows.forEach { budget(it.first.size + it.second.size) }
        operations.filter { it.storeName == storeName && inRange(it.key) }.forEach { operation ->
            rows.removeAll { it.first.contentEquals(operation.key) }
            if (operation is IndexedDbWriteOperation.Put) rows += operation.key to operation.value
        }
        rows.sortWith { a, b -> a.first.compareTo(b.first) }
        if (reverse) rows.reverse()
        return rows.take(minOf(limit.toLong(), Int.MAX_VALUE.toLong()).toInt())
    }
}
