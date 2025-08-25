package maryk.datastore.foundationdb.processors.helpers

internal fun FDBIterator.nonHistoricQualifierRetriever(
    prefix: ByteArray
): (((Int) -> Byte, Int) -> Unit) -> Boolean = { resultHandler ->
    if (this.hasNext()) {
        val value = next()

        val qualifier = value.key

        resultHandler({ qualifier[prefix.size + it] }, qualifier.size - prefix.size)

        true
    } else {
        false
    }
}
