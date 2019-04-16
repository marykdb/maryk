package maryk.core.processors.datastore

private typealias QualifierProcessor = (ByteArray) -> Unit
typealias CacheProcessor = (Int, QualifierProcessor) -> Unit

/**
 * Processes qualifiers from [getQualifier] and caches qualifier processors so it does not need to redo the work
 * Uses [readQualifierAndProcessContent] if there is no suitable cached processor
 */
fun processQualifiers(
    getQualifier: () -> ByteArray?,
    readQualifierAndProcessContent: (ByteArray, CacheProcessor) -> Unit
) {
    var lastQualifier: ByteArray? = null
    var qualifier = getQualifier()
    // Stack of processors to process qualifier and if matched the values.
    // Since definitions are nested we need a stack
    val processorStack = mutableListOf<Pair<Int, QualifierProcessor>>()

    while (qualifier != null) {
        // Remove anything from processor stack that does not match anymore
        lastQualifier?.let { last ->
            val nonMatchIndex = last.firstNonMatchIndex(qualifier!!)
            for (i in (processorStack.size - 1 downTo 0)) {
                if (processorStack[i].first >= nonMatchIndex) {
                    processorStack.removeAt(i)
                }
            }
        }

        // Try to process qualifier with last qualifier processor in list
        processorStack.lastOrNull()?.second?.invoke(qualifier)
            ?: readQualifierAndProcessContent(qualifier) { index, processor ->
                processorStack += Pair(index, processor)
            }

        // Last qualifier to remove processors in next iteration
        lastQualifier = qualifier

        // Get next qualifier
        qualifier = getQualifier()
    }
}

/** Find the first non match index against [comparedTo] */
private fun ByteArray.firstNonMatchIndex(comparedTo: ByteArray): Int {
    var index = -1
    while (++index < this.size) {
        if (comparedTo[index] != this[index]) break
    }
    return index
}
