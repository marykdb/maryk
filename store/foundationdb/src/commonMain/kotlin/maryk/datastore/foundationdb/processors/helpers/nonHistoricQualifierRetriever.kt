package maryk.datastore.foundationdb.processors.helpers

internal fun FDBIterator.nonHistoricQualifierRetriever(
    prefix: ByteArray
): (((Int) -> Byte, Int) -> Unit) -> Boolean = { resultHandler ->
    var emitted = false
    while (this.hasNext()) {
        val value = next()
        val qualifier = value.key
        resultHandler({ qualifier[prefix.size + it] }, qualifier.size - prefix.size)
        emitted = true
        break
    }
    emitted
}
