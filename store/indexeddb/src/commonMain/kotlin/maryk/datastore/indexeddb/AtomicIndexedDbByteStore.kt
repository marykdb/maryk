package maryk.datastore.indexeddb

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import maryk.core.properties.types.Bytes
import maryk.datastore.shared.rejectAtomicLimit
import maryk.datastore.shared.MAX_ATOMIC_WORK_BYTES
import maryk.datastore.shared.MAX_ATOMIC_SCAN_ROWS
import maryk.datastore.shared.currentAtomicContext
import maryk.lib.extensions.compare.compareTo

internal val TransactionRevisionKey = "transaction/revision".encodeToByteArray()

/** Explicit opt-in: writeBatch must use one native transaction and transaction must fence all Maryk writers. */
interface AtomicIndexedDbByteStore : IndexedDbByteStore

/** Coroutine-local routing keeps background readers out of uncommitted staging state. */
internal class AtomicAwareIndexedDbByteStore(
    private val delegate: IndexedDbByteStore,
    private val owner: Any,
) : IndexedDbByteStore by delegate, IndexedDbBatchReadStore {
    private suspend fun active() = currentAtomicContext(owner)?.transaction as? StagedIndexedDbByteStore ?: delegate
    override suspend fun <T> transaction(storeNames: Set<String>, mode: IndexedDbTransactionMode, block: suspend (IndexedDbByteStore) -> T): T =
        if (active() !== delegate) block(this) else delegate.transaction(storeNames, mode) { block(this) }
    override suspend fun getBatchValues(storeName: String, keys: List<ByteArray>) = active().getBatch(storeName, keys)
    override suspend fun get(storeName: String, key: ByteArray) = active().get(storeName, key)
    override suspend fun put(storeName: String, key: ByteArray, value: ByteArray) =
        writeBatch(listOf(IndexedDbWriteOperation.Put(storeName, key, value)))
    override suspend fun delete(storeName: String, key: ByteArray) =
        writeBatch(listOf(IndexedDbWriteOperation.Delete(storeName, key)))
    @OptIn(ExperimentalUuidApi::class)
    override suspend fun writeBatch(operations: List<IndexedDbWriteOperation>) {
        val target = active()
        val changesData = operations.any { it.storeName != "meta" && it.storeName != CommitJournalStoreName && it.storeName != CommitConsumerStoreName }
        target.writeBatch(if (target === delegate && changesData) operations +
            IndexedDbWriteOperation.Put("meta", TransactionRevisionKey, Uuid.random().toByteArray()) else operations)
    }
    override suspend fun scan(storeName: String, startKey: ByteArray?, includeStart: Boolean, endKey: ByteArray?, includeEnd: Boolean, reverse: Boolean, limit: UInt) =
        active().scan(storeName, startKey, includeStart, endKey, includeEnd, reverse, limit)
}

internal class StagedIndexedDbByteStore(private val delegate: IndexedDbByteStore) : IndexedDbByteStore by delegate {
    private val pending = linkedMapOf<Pair<String, Bytes>, IndexedDbWriteOperation>()
    val operations: List<IndexedDbWriteOperation> get() = pending.values.toList()
    var journalKey: ByteArray? = null
    private var workBytes = 0L
    private fun budget(bytes: Long) {
        workBytes += bytes
        if (workBytes > MAX_ATOMIC_WORK_BYTES) rejectAtomicLimit("workBytes", MAX_ATOMIC_WORK_BYTES.toULong(), workBytes.toULong())
    }
    override suspend fun get(storeName: String, key: ByteArray): ByteArray? =
        when (val operation = pending[storeName to Bytes(key)]) {
            is IndexedDbWriteOperation.Put -> operation.value
            is IndexedDbWriteOperation.Delete -> null
            null -> delegate.get(storeName, key)?.also { budget(it.size.toLong() + key.size) }
        }
    override suspend fun put(storeName: String, key: ByteArray, value: ByteArray) =
        writeBatch(listOf(IndexedDbWriteOperation.Put(storeName, key, value)))
    override suspend fun delete(storeName: String, key: ByteArray) =
        writeBatch(listOf(IndexedDbWriteOperation.Delete(storeName, key)))
    override suspend fun writeBatch(operations: List<IndexedDbWriteOperation>) {
        operations.forEach { operation ->
            budget(operation.key.size.toLong() + ((operation as? IndexedDbWriteOperation.Put)?.value?.size ?: 0))
            pending[operation.storeName to Bytes(operation.key)] = operation
        }
    }
    override suspend fun scan(storeName: String, startKey: ByteArray?, includeStart: Boolean, endKey: ByteArray?, includeEnd: Boolean, reverse: Boolean, limit: UInt): List<Pair<ByteArray, ByteArray>> {
        if (limit == 0u) return emptyList()
        fun inRange(key: ByteArray) =
            (startKey == null || key > startKey || includeStart && key.contentEquals(startKey)) &&
                (endKey == null || key < endKey || includeEnd && key.contentEquals(endKey))
        val overlay = pending.values.filter { it.storeName == storeName && inRange(it.key) }
            .sortedWith { a, b -> if (reverse) b.key.compareTo(a.key) else a.key.compareTo(b.key) }
        var overlayIndex = 0
        var cursor: ByteArray? = null
        var page = emptyList<Pair<ByteArray, ByteArray>>()
        var pageIndex = 0
        var exhausted = false
        var scanned = 0L
        val result = mutableListOf<Pair<ByteArray, ByteArray>>()
        suspend fun nextStored(): Pair<ByteArray, ByteArray>? {
            if (pageIndex == page.size && !exhausted) {
                val pageLimit = minOf(256u, limit - result.size.toUInt())
                page = delegate.scan(storeName,
                    if (reverse) startKey else cursor ?: startKey,
                    if (reverse || cursor == null) includeStart else false,
                    if (reverse) cursor ?: endKey else endKey,
                    if (!reverse || cursor == null) includeEnd else false,
                    reverse, pageLimit)
                pageIndex = 0
                exhausted = page.size < pageLimit.toInt()
                if (page.isNotEmpty()) cursor = page.last().first
                scanned += page.size
                if (scanned > MAX_ATOMIC_SCAN_ROWS) rejectAtomicLimit("scanRows", MAX_ATOMIC_SCAN_ROWS.toULong(), scanned.toULong())
                page.forEach { budget(it.first.size.toLong() + it.second.size) }
            }
            return page.getOrNull(pageIndex++)
        }
        var stored = nextStored()
        while (result.size.toUInt() < limit) {
            val staged = overlay.getOrNull(overlayIndex)
            if (stored == null && staged == null) break
            val comparison = if (stored != null && staged != null) {
                if (reverse) staged.key.compareTo(stored.first) else stored.first.compareTo(staged.key)
            } else if (stored == null) 1 else -1
            if (comparison >= 0) {
                overlayIndex++
                if (staged is IndexedDbWriteOperation.Put) result += staged.key to staged.value
            } else result += requireNotNull(stored)
            // Don't fetch a page beyond the requested result boundary.
            if (comparison <= 0 && result.size.toUInt() < limit) stored = nextStored()
        }
        return result
    }
}
