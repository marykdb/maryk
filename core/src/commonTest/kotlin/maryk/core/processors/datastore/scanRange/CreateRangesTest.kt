package maryk.core.processors.datastore.scanRange

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertContentEquals

class CreateRangesTest {
    @Test
    fun createRangesWithEmptyStartReturnsNoRanges() {
        val ranges = createRanges(
            start = emptyList(),
            end = listOf(listOf(1)),
            startInclusive = true,
            endInclusive = true
        )

        assertEquals(0, ranges.size)
    }

    @Test
    fun createRangesWithEmptyEndReturnsNoRanges() {
        val ranges = createRanges(
            start = listOf(listOf(1)),
            end = emptyList(),
            startInclusive = true,
            endInclusive = true
        )

        assertEquals(0, ranges.size)
    }

    @Test
    fun createRangesDropsRangesBeforeStartKey() {
        val ranges = createRanges(
            start = listOf(listOf(1), listOf(3), listOf(5)),
            end = listOf(listOf(1), listOf(3), listOf(5)),
            startInclusive = true,
            endInclusive = true,
            startKey = byteArrayOf(4)
        )

        assertEquals(1, ranges.size)
        assertContentEquals(byteArrayOf(5), ranges.single().start)
        assertContentEquals(byteArrayOf(5), ranges.single().end)
    }

    @Test
    fun createRangesClampsContainingRangeToStartKey() {
        val ranges = createRanges(
            start = listOf(listOf(1), listOf(4)),
            end = listOf(listOf(3), listOf(6)),
            startInclusive = true,
            endInclusive = true,
            startKey = byteArrayOf(2)
        )

        assertEquals(2, ranges.size)
        assertContentEquals(byteArrayOf(2), ranges.first().start)
        assertContentEquals(byteArrayOf(3), ranges.first().end)
        assertContentEquals(byteArrayOf(4), ranges.last().start)
        assertContentEquals(byteArrayOf(6), ranges.last().end)
    }

    @Test
    fun createRangesKeepsUnboundedRangeAfterStartKey() {
        val ranges = createRanges(
            start = listOf(listOf(1)),
            end = listOf(emptyList()),
            startInclusive = true,
            endInclusive = true,
            startKey = byteArrayOf(4)
        )

        assertEquals(1, ranges.size)
        assertContentEquals(byteArrayOf(4), ranges.single().start)
        assertContentEquals(byteArrayOf(), ranges.single().end)
    }
}
