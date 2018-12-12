package maryk.core.processors.datastore

import maryk.core.models.key
import maryk.core.query.filters.Equals
import maryk.core.query.filters.GreaterThan
import maryk.core.query.filters.GreaterThanEquals
import maryk.core.query.filters.LessThan
import maryk.core.query.filters.LessThanEquals
import maryk.core.query.pairs.with
import maryk.lib.extensions.compare.compareTo
import maryk.lib.extensions.toHex
import maryk.lib.time.DateTime
import maryk.test.models.Log
import maryk.test.models.Severity.ERROR
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
            KeyPartialToMatch(1, byteArrayOf(2, 4)),
            KeyPartialToMatch(3, byteArrayOf(5, 6))
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

    private val match = Log.key(Log("message", ERROR, DateTime(2018, 12, 8, 12, 33, 23)))
    // Dont be confused that the time is reversed. Later is referring to it is later in table, not in time.
    private val earlier = Log.key(Log("message", ERROR, DateTime(2019, 12, 8, 12, 33, 23)))
    private val later = Log.key(Log("message", ERROR, DateTime(2017, 12, 8, 12, 33, 23)))

    @Test
    fun convertSimpleEqualFilterToRange() {
        val filter = Equals(
            Log.ref { timestamp } with DateTime(2018, 12, 8, 12, 33, 23)
        )

        val scanRange = Log.createScanRange(filter, null)

        scanRange.start.toHex() shouldBe "7fffffa3f445ec7fff010000"
        scanRange.end?.toHex() shouldBe "7fffffa3f445ec7fff01ffff"

        (scanRange.start < match.bytes) shouldBe true
        scanRange.keyOutOfRange(match.bytes) shouldBe false
        scanRange.keyMatches(match.bytes) shouldBe true

        (scanRange.start < earlier.bytes) shouldBe false
        scanRange.keyOutOfRange(earlier.bytes) shouldBe false
        scanRange.keyMatches(earlier.bytes) shouldBe true

        (scanRange.start < later.bytes) shouldBe true
        scanRange.keyOutOfRange(later.bytes) shouldBe true
        scanRange.keyMatches(later.bytes) shouldBe true
    }

    @Test
    fun convertGreaterThanFilterToRange() {
        val filter = GreaterThan(
            Log.ref { timestamp } with DateTime(2018, 12, 8, 12, 33, 23)
        )

        val scanRange = Log.createScanRange(filter, null)

        scanRange.start.toHex() shouldBe "7fffffa3f445ec7fff020000"
        scanRange.end?.toHex() shouldBe "ffffffffffffffffffffffff"

        (scanRange.start < match.bytes) shouldBe false // Because should skip
        scanRange.keyOutOfRange(match.bytes) shouldBe false
        scanRange.keyMatches(match.bytes) shouldBe true

        (scanRange.start < earlier.bytes) shouldBe false
        scanRange.keyOutOfRange(earlier.bytes) shouldBe false
        scanRange.keyMatches(earlier.bytes) shouldBe true

        (scanRange.start < later.bytes) shouldBe true
        scanRange.keyOutOfRange(later.bytes) shouldBe false
        scanRange.keyMatches(later.bytes) shouldBe true
    }

    @Test
    fun convertGreaterThanEqualsFilterToRange() {
        val filter = GreaterThanEquals(
            Log.ref { timestamp } with DateTime(2018, 12, 8, 12, 33, 23)
        )

        val scanRange = Log.createScanRange(filter, null)

        scanRange.start.toHex() shouldBe "7fffffa3f445ec7fff010000"
        scanRange.end?.toHex() shouldBe "ffffffffffffffffffffffff"

        (scanRange.start < match.bytes) shouldBe true
        scanRange.keyOutOfRange(match.bytes) shouldBe false
        scanRange.keyMatches(match.bytes) shouldBe true

        (scanRange.start < earlier.bytes) shouldBe false
        scanRange.keyOutOfRange(earlier.bytes) shouldBe false
        scanRange.keyMatches(earlier.bytes) shouldBe true

        (scanRange.start < later.bytes) shouldBe true
        scanRange.keyOutOfRange(later.bytes) shouldBe false
        scanRange.keyMatches(later.bytes) shouldBe true
    }

    @Test
    fun convertLessThanFilterToRange() {
        val filter = LessThan(
            Log.ref { timestamp } with DateTime(2018, 12, 8, 12, 33, 23)
        )

        val scanRange = Log.createScanRange(filter, null)

        scanRange.start.toHex() shouldBe "000000000000000000000000"
        scanRange.end?.toHex() shouldBe "7fffffa3f445ec7fff00ffff"

        (scanRange.start < match.bytes) shouldBe true
        scanRange.keyOutOfRange(match.bytes) shouldBe true // because should not be included
        scanRange.keyMatches(match.bytes) shouldBe true

        (scanRange.start < earlier.bytes) shouldBe true
        scanRange.keyOutOfRange(earlier.bytes) shouldBe false
        scanRange.keyMatches(earlier.bytes) shouldBe true

        (scanRange.start < later.bytes) shouldBe true
        scanRange.keyOutOfRange(later.bytes) shouldBe true
        scanRange.keyMatches(later.bytes) shouldBe true
    }

    @Test
    fun convertLessThanEqualsFilterToRange() {
        val filter = LessThanEquals(
            Log.ref { timestamp } with DateTime(2018, 12, 8, 12, 33, 23)
        )

        val scanRange = Log.createScanRange(filter, null)

        scanRange.start.toHex() shouldBe "000000000000000000000000"
        scanRange.end?.toHex() shouldBe "7fffffa3f445ec7fff01ffff"

        (scanRange.start < match.bytes) shouldBe true
        scanRange.keyOutOfRange(match.bytes) shouldBe false
        scanRange.keyMatches(match.bytes) shouldBe true

        (scanRange.start < earlier.bytes) shouldBe true
        scanRange.keyOutOfRange(earlier.bytes) shouldBe false
        scanRange.keyMatches(earlier.bytes) shouldBe true

        (scanRange.start < later.bytes) shouldBe true
        scanRange.keyOutOfRange(later.bytes) shouldBe true
        scanRange.keyMatches(later.bytes) shouldBe true
    }
}
