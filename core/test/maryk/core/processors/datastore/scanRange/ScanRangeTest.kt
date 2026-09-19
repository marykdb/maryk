package maryk.core.processors.datastore.scanRange

import kotlin.test.Test
import kotlin.test.assertNull

class ScanRangeTest {
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
