package maryk.datastore.foundationdb.processors.helpers

import com.apple.foundationdb.KeyValue
import com.apple.foundationdb.async.AsyncIterator

class FDBIterator(
    private val iterator: AsyncIterator<KeyValue>
) : Iterator<KeyValue>, AutoCloseable {
    lateinit var current: KeyValue
        private set

    private var closed = false

    override fun hasNext() = iterator.hasNext()
    override fun next(): KeyValue = iterator.next().also { this.current = it }

    fun cancel() {
        if (!closed) {
            iterator.cancel()
            closed = true
        }
    }

    override fun close() = cancel()
}
