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
    processBoth: (T, T) -> Unit = { _, _ -> Unit},
    processOnlyOnIterator1: (T) -> Unit = {},
    processOnlyOnIterator2: (T) -> Unit = {}
) {
    var value1: T? = iterator1.next()
    var value2: T? = iterator2.next()

    fun process1(value: T) {
        processOnlyOnIterator1(value)
        value1 = if (iterator1.hasNext()) iterator1.next() else null
    }

    fun process2(value: T) {
        processOnlyOnIterator2(value)
        value2 = if (iterator2.hasNext()) iterator2.next() else null
    }

    // Walk all stored and new properties of both data models and process them
    // depending on if they are present only in stored, new or both.
    while (value1 != null || value2 != null) {
        val val1 = value1
        val val2 = value2

        when {
            val1 == null ->
                process2(val2!!)
            val2 == null ->
                process1(val1)
            else -> {
                val result = comparator.compare(val1, val2)
                when {
                    result == 0 -> {
                        processBoth(val2, val1)
                        value2 = if (iterator2.hasNext()) iterator2.next() else null
                        value1 = if (iterator1.hasNext()) iterator1.next() else null
                    }
                    result > 0 ->
                        if (iterator1.hasNext()) process1(val1) else process2(val2)
                    result < 0 ->
                        if (iterator2.hasNext()) process2(val2) else process1(val1)
                }
            }
        }
    }
}
