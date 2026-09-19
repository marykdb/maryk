package maryk.lib

/**
 * Walks two iterators ([iterator1] and [iterator2]) of sorted values so the values can be processed
 * depending if they are present on both iterable collections with [processBoth], only on the first
 * with [processOnlyOnIterator1] or only on the second with [processOnlyOnIterator2]
 */
fun <T> synchronizedIteration(
    iterator1: Iterator<T>,
    iterator2: Iterator<T>,
    comparator: Comparator<T>,
    processBoth: (T, T) -> Unit = { _, _ -> },
    processOnlyOnIterator1: (T) -> Unit = {},
    processOnlyOnIterator2: (T) -> Unit = {}
) {
    var hasValue1 = iterator1.hasNext()
    var hasValue2 = iterator2.hasNext()
    if (!hasValue1 && !hasValue2) return

    var value1: T? = if (hasValue1) iterator1.next() else null
    var value2: T? = if (hasValue2) iterator2.next() else null

    while (hasValue1 || hasValue2) {
        when {
            !hasValue2 -> {
                @Suppress("UNCHECKED_CAST")
                processOnlyOnIterator1(value1 as T)
                hasValue1 = iterator1.hasNext()
                value1 = if (hasValue1) iterator1.next() else null
            }
            !hasValue1 -> {
                @Suppress("UNCHECKED_CAST")
                processOnlyOnIterator2(value2 as T)
                hasValue2 = iterator2.hasNext()
                value2 = if (hasValue2) iterator2.next() else null
            }
            else -> {
                @Suppress("UNCHECKED_CAST")
                val first = value1 as T
                @Suppress("UNCHECKED_CAST")
                val second = value2 as T
                val compareResult = comparator.compare(first, second)
                if (compareResult <= 0) {
                    if (compareResult == 0) {
                        processBoth(first, second)
                        hasValue2 = iterator2.hasNext()
                        value2 = if (hasValue2) iterator2.next() else null
                    } else {
                        processOnlyOnIterator1(first)
                    }
                    hasValue1 = iterator1.hasNext()
                    value1 = if (hasValue1) iterator1.next() else null
                } else {
                    processOnlyOnIterator2(second)
                    hasValue2 = iterator2.hasNext()
                    value2 = if (hasValue2) iterator2.next() else null
                }
            }
        }
    }
}
