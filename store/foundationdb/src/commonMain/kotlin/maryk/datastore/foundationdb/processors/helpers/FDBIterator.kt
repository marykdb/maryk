package maryk.datastore.foundationdb.processors.helpers

import maryk.foundationdb.KeyValue
import maryk.foundationdb.async.AsyncIterator

class FDBIterator(
    private val iterator: AsyncIterator<KeyValue>
) : Iterator<KeyValue> {
    lateinit var current: KeyValue

    override fun hasNext() = iterator.hasNext()
    override fun next(): KeyValue = iterator.nextBlocking().also { this.current = it }
}
