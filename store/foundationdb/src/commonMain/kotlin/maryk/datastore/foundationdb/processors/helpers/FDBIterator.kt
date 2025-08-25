package maryk.datastore.foundationdb.processors.helpers

import com.apple.foundationdb.KeyValue
import com.apple.foundationdb.async.AsyncIterator

class FDBIterator(
    private val iterator: AsyncIterator<KeyValue>
) : Iterator<KeyValue> {
    lateinit var current: KeyValue

    override fun hasNext() = iterator.hasNext()
    override fun next(): KeyValue = iterator.next().also { this.current = it }
}
