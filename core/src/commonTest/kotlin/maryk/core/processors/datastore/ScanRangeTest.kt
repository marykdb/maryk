package maryk.core.processors.datastore

import maryk.test.shouldBe
import kotlin.test.Test

class ScanRangeTest {
    private val scanRange = ScanRange(
        start = byteArrayOf(1, 2, 3, 4, 5),
        end = byteArrayOf(9, 8, 7, 6, 5),
        uniques = listOf(
            UniqueToMatch(byteArrayOf(1, 2), "unique"),
            UniqueToMatch(byteArrayOf(1, 2), "unique")
        ),
        partialMatches = listOf(
            PartialToMatch(1, byteArrayOf(2, 4)),
            PartialToMatch(3, byteArrayOf(5, 6))
        )
    )

    @Test
    fun keyOutOfRange() {
        scanRange.keyOutOfRange(byteArrayOf(3, 4, 5, 6, 7)) shouldBe false
        scanRange.keyOutOfRange(byteArrayOf(9, 9, 8, 7, 6)) shouldBe true
    }

    @Test
    fun keyMatches() {
        scanRange.keyMatches(byteArrayOf(3, 2, 4, 5, 6)) shouldBe true
        scanRange.keyMatches(byteArrayOf(3, 4, 4, 5, 6)) shouldBe false
        scanRange.keyMatches(byteArrayOf(3, 2, 4, 6, 6)) shouldBe false
    }
}
