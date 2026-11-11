package maryk.datastore.shared

import maryk.core.properties.references.IsPropertyReferenceForCache
import maryk.core.properties.types.Key

/** Request-owned pool; never retains a transaction, snapshot, or value reader. */
internal class RequestValueCache(private val maxBytes: Int) {
    private class Model(val id: UInt) {
        val records = CacheLruMap<Key<*>, Record>(16)
    }
    private class Record(val model: Model, val key: Key<*>) {
        val entries = CacheLruMap<IsPropertyReferenceForCache<*, *>, Entry>(4)
    }
    private class Entry(
        val record: Record,
        val reference: IsPropertyReferenceForCache<*, *>,
        val version: ULong,
        val value: Any?,
        val bytes: Int,
    ) {
        var previous: Entry? = null
        var next: Entry? = null
    }
    private val models = HashMap<UInt, Model>()
    private var first: Entry? = null
    private var last: Entry? = null
    private var bytes = 0L
    private var revision = 0L

    fun readValue(modelId: UInt, key: Key<*>, reference: IsPropertyReferenceForCache<*, *>, version: ULong, reader: () -> Any?): Any? {
        if (maxBytes == 0) return reader()
        val model = models[modelId]
        val record = model?.records?.get(key)
        val previous = record?.entries?.get(reference)
        if (previous != null && previous.version == version) {
            touch(previous)
            return previous.value
        }
        val beforeRead = revision
        val value = reader()
        // A decoder may re-enter the cache and evict/replace the entry captured above.
        val current = if (beforeRead == revision) previous else models[modelId]?.records?.get(key)?.entries?.get(reference)
        if (current != null && current.version >= version) return value
        val estimate = estimatedCacheValueBytes(value)
        // Additional allowance for pool links and record/reference indexes.
        val size = if (estimate < 0) -1 else estimate + 128L
        if (current != null) remove(current)
        if (size < 0 || size + RECORD_BYTES > maxBytes) return value
        val owner = models.getOrPut(modelId) { Model(modelId) }
        val target = owner.records.get(key) ?: Record(owner, key).also {
            owner.records.put(key, it)
            bytes += RECORD_BYTES
            if (owner.records.size > 1024) discard(owner.records.removeOldest()!!)
        }
        val entry = Entry(target, reference, version, value, size.toInt())
        revision++
        target.entries.put(reference, entry)
        append(entry)
        bytes += entry.bytes
        if (target.entries.size > 128) unlink(target.entries.removeOldest()!!)
        while (bytes > maxBytes) remove(first!!)
        return value
    }

    fun delete(modelId: UInt, key: Key<*>) {
        val model = models[modelId] ?: return
        model.records.remove(key)?.let(::discard)
        if (model.records.isEmpty()) models.remove(modelId)
    }

    private fun discard(record: Record) {
        revision++
        while (!record.entries.isEmpty()) unlink(record.entries.removeOldest()!!)
        bytes -= RECORD_BYTES
    }

    private fun remove(entry: Entry) {
        revision++
        entry.record.entries.remove(entry.reference)
        unlink(entry)
        if (entry.record.entries.isEmpty()) {
            val model = entry.record.model
            model.records.remove(entry.record.key)
            bytes -= RECORD_BYTES
            if (model.records.isEmpty()) models.remove(model.id)
        }
    }

    private fun touch(entry: Entry) {
        if (entry === last) return
        unlink(entry)
        append(entry)
        bytes += entry.bytes
    }

    private fun append(entry: Entry) {
        entry.previous = last
        last?.next = entry
        if (first == null) first = entry
        last = entry
    }

    private fun unlink(entry: Entry) {
        val previous = entry.previous
        val next = entry.next
        if (previous == null) first = next else previous.next = next
        if (next == null) last = previous else next.previous = previous
        entry.previous = null
        entry.next = null
        bytes -= entry.bytes
    }

    private companion object {
        // Record/model maps and recency indexes also consume memory, even for tiny values.
        const val RECORD_BYTES = 512L
    }
}
