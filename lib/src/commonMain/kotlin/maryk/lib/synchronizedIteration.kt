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
    if (!iterator1.hasNext() && !iterator2.hasNext()) return

    var value1: T? = if (iterator1.hasNext()) iterator1.next() else null
    var value2: T? = if (iterator2.hasNext()) iterator2.next() else null

    while (value1 != null || value2 != null) {
        when {
            value2 == null || (value1 != null && comparator.compare(value1, value2) <= 0) -> {
                if (value2 != null && comparator.compare(value1!!, value2) == 0) {
                    processBoth(value1, value2)
                    value2 = if (iterator2.hasNext()) iterator2.next() else null
                } else {
                    processOnlyOnIterator1(value1!!)
                }
                value1 = if (iterator1.hasNext()) iterator1.next() else null
            }
            else -> {
                processOnlyOnIterator2(value2)
                value2 = if (iterator2.hasNext()) iterator2.next() else null
            }
        }
    }
}
