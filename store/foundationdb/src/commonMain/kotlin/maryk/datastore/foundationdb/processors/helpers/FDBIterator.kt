package maryk.datastore.foundationdb.processors.helpers

import maryk.foundationdb.KeyValue

class FDBIterator(
    private val iterator: Iterator<KeyValue>
) : Iterator<KeyValue> {
    lateinit var current: KeyValue

    override fun hasNext() = iterator.hasNext()
    override fun next(): KeyValue = iterator.next().also { this.current = it }
}
