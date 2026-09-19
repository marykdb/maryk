package maryk.core.processors.datastore

private typealias QualifierProcessor = ((Int) -> Byte, Int) -> Unit
typealias CacheProcessor = (Int, QualifierProcessor) -> Unit

/**
 * Processes qualifiers from [getQualifier] and caches qualifier processors so it does not need to redo the work
 * Uses [readQualifierAndProcessContent] if there is no suitable cached processor
 */
fun processQualifiers(
    getQualifier: (((Int) -> Byte, Int) -> Unit) -> Boolean,
    readQualifierAndProcessContent: ((Int) -> Byte, Int, CacheProcessor) -> Unit
) {
    var lastQualifierReader: ((Int) -> Byte)? = null
    var lastQualifierLength: Int = -1

    var currentQualifierReader: ((Int) -> Byte)? = null
    var currentQualifierLength: Int = -1

    val qualifierSetter: ((Int) -> Byte, Int) -> Unit = { qr, l ->
        currentQualifierReader = qr
        currentQualifierLength = l
    }

    var qualifierPresent = getQualifier(qualifierSetter)
    // Stack of processors to process qualifier and if matched the values.
    // Since definitions are nested we need a stack
    val processorStack = mutableListOf<Pair<Int, QualifierProcessor>>()

    while (qualifierPresent) {
        val qualifierReader = currentQualifierReader ?: throw IllegalStateException("Unexpected null for qualifier bytes")
        // Remove anything from processor stack that does not match anymore
        lastQualifierReader?.let { lastReader ->
            val nonMatchIndex = firstNonMatchIndex(
                lastReader, lastQualifierLength,
                qualifierReader, currentQualifierLength
            )
            for (i in (processorStack.lastIndex downTo 0)) {
                if (processorStack[i].first >= nonMatchIndex) {
                    processorStack.removeAt(i)
                }
            }
        }

        // Try to process qualifier with last qualifier processor in list
        processorStack.lastOrNull()?.second?.invoke(qualifierReader, currentQualifierLength)
            ?: readQualifierAndProcessContent(qualifierReader, currentQualifierLength) { index, processor ->
                processorStack += Pair(index, processor)
            }

        // Last qualifier to remove processors in next iteration
        lastQualifierReader = currentQualifierReader
        lastQualifierLength = currentQualifierLength

        // Get next qualifier
        qualifierPresent = getQualifier(qualifierSetter)
    }
}

/** Find the first non match index against [compareQualifierReader] */
private fun firstNonMatchIndex(
    lastQualifierReader: (Int) -> Byte,
    lastQualifierLength: Int,
    compareQualifierReader: (Int) -> Byte,
    compareQualifierLength: Int
): Int {
    var index = -1
    val shortestLength = minOf(compareQualifierLength, lastQualifierLength)
    while (++index < shortestLength) {
        if (compareQualifierReader(index) != lastQualifierReader(index)) break
    }
    return index
}
