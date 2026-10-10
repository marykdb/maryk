package maryk.core.processors.datastore.scanRange

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertNull

class ScanRangeTest {
    @Test
    fun descendingContinuationInsideInclusivePrefixUsesCursor() {
        val range = ScanRange(byteArrayOf(1, 2), true, byteArrayOf(1, 2), true)
        val cursor = byteArrayOf(1, 2, 10, 0)
        assertContentEquals(cursor, range.getDescendingStartKey(cursor))
        assertContentEquals(byteArrayOf(1, 2, 9, -1), range.getDescendingStartKey(cursor, false))
        assertContentEquals(byteArrayOf(1, 3), range.getDescendingStartKey(byteArrayOf(1, 4)))
    }

    @Test
    fun descendingInclusiveFullValueUsesCursorAtEnd() {
        val range = ScanRange(byteArrayOf(1), true, byteArrayOf(5), true)
        assertContentEquals(byteArrayOf(4), range.getDescendingStartKey(byteArrayOf(5), false))
    }

    @Test
    fun ascendingContinuationCarriesIntoPreviousByte() {
        val range = ScanRange(byteArrayOf(), true, null, false)
        assertContentEquals(byteArrayOf(1, 0), range.getAscendingStartKey(byteArrayOf(0, -1), false))
    }

    @Test
    fun descendingMaximumPrefixUsesCursor() {
        val range = ScanRange(byteArrayOf(-1), true, byteArrayOf(-1), true)
        assertNull(range.getDescendingStartKey())
        assertContentEquals(byteArrayOf(-1, 1), range.getDescendingStartKey(byteArrayOf(-1, 1)))
    }

    @Test
    fun descendingExclusiveMinimumHasNoStartKey() {
        val range = ScanRange(
            start = byteArrayOf(),
            startInclusive = true,
            end = null,
            endInclusive = false,
        )

        assertNull(range.getDescendingStartKey(byteArrayOf(0), inclusiveStartKey = false))
    }
}
