package maryk.core.processors.datastore.scanRange

import kotlin.test.Test
import kotlin.test.assertEquals

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
}
