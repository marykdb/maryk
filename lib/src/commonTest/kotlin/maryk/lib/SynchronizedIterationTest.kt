package maryk.lib

import kotlin.test.Test
import kotlin.test.assertContentEquals

class SynchronizedIterationTest {
    @Test
    fun testSynchronizedIteration() {
        val both = mutableListOf<Pair<Int, Int>>()
        val onlyLeft = mutableListOf<Int>()
        val onlyRight = mutableListOf<Int>()

        synchronizedIteration(
            iterator1 = listOf(1, 2, 4, 6).iterator(),
            iterator2 = listOf(2, 3, 4, 5).iterator(),
            comparator = { left, right -> left.compareTo(right) },
            processBoth = { left, right -> both += left to right },
            processOnlyOnIterator1 = { onlyLeft += it },
            processOnlyOnIterator2 = { onlyRight += it }
        )

        assertContentEquals(listOf(2 to 2, 4 to 4), both)
        assertContentEquals(listOf(1, 6), onlyLeft)
        assertContentEquals(listOf(3, 5), onlyRight)
    }
}
