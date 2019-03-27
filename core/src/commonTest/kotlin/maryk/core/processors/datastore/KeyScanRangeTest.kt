package maryk.core.processors.datastore

import maryk.core.models.key
import maryk.core.query.filters.And
import maryk.core.query.filters.Equals
import maryk.core.query.filters.GreaterThan
import maryk.core.query.filters.GreaterThanEquals
import maryk.core.query.filters.LessThan
import maryk.core.query.filters.LessThanEquals
import maryk.core.query.filters.Range
import maryk.core.query.filters.ValueIn
import maryk.core.query.pairs.with
import maryk.lib.extensions.toHex
import maryk.lib.time.DateTime
import maryk.test.models.Log
import maryk.test.models.Severity.DEBUG
import maryk.test.models.Severity.ERROR
import maryk.test.models.Severity.INFO
import maryk.test.shouldBe
import kotlin.test.Test

class KeyScanRangeTest {
    private val scanRange = KeyScanRange(
        start = byteArrayOf(1, 2, 3, 4, 5),
        startInclusive = true,
        end = byteArrayOf(9, 8, 7, 6, 5),
        endInclusive = true,
        partialMatches = listOf(
            IndexPartialToMatch(1, 1, 5, byteArrayOf(2, 4)),
            IndexPartialToMatch(2, 3, 5, byteArrayOf(5, 6))
        ),
        equalPairs = listOf(),
        uniques = listOf(),
        keySize = 5
    )

    @Test
    fun keyOutOfRange() {
        scanRange.keyOutOfRange(byteArrayOf(3, 4, 5, 6, 7)) shouldBe false
        scanRange.keyOutOfRange(byteArrayOf(9, 9, 8, 7, 6)) shouldBe true
    }

    @Test
    fun keyMatches() {
        scanRange.matchesPartials(byteArrayOf(3, 2, 4, 5, 6)) shouldBe true
        scanRange.matchesPartials(byteArrayOf(3, 4, 4, 5, 6)) shouldBe false
        scanRange.matchesPartials(byteArrayOf(3, 2, 4, 6, 6)) shouldBe false
    }

    private val match = Log.key(Log("message", ERROR, DateTime(2018, 12, 8, 12, 33, 23)))
    // Dont be confused that the time is reversed. Later is referring to it is later in table, not in time.
    private val earlier = Log.key(Log("message", DEBUG, DateTime(2019, 12, 8, 12, 33, 23)))
    private val later = Log.key(Log("message", INFO, DateTime(2017, 12, 8, 12, 33, 23)))

    @Test
    fun convertSimpleEqualFilterToScanRange() {
        val filter = Equals(
            Log.ref { timestamp } with DateTime(2018, 12, 8, 12, 33, 23)
        )

        val scanRange = Log.createScanRange(filter, null)

        scanRange.start.toHex() shouldBe "7fffffa3f445ec7fff0000"
        scanRange.startInclusive shouldBe true
        scanRange.end?.toHex() shouldBe "7fffffa3f445ec7fffffff"
        scanRange.endInclusive shouldBe true

        scanRange.keyBeforeStart(match.bytes) shouldBe false
        scanRange.keyOutOfRange(match.bytes) shouldBe false
        scanRange.matchesPartials(match.bytes) shouldBe true

        scanRange.keyBeforeStart(earlier.bytes) shouldBe true
        scanRange.keyOutOfRange(earlier.bytes) shouldBe false
        scanRange.matchesPartials(earlier.bytes) shouldBe true

        scanRange.keyBeforeStart(later.bytes) shouldBe false
        scanRange.keyOutOfRange(later.bytes) shouldBe true
        scanRange.matchesPartials(later.bytes) shouldBe true
    }

    @Test
    fun convertGreaterThanFilterToScanRange() {
        val filter = GreaterThan(
            Log.ref { timestamp } with DateTime(2018, 12, 8, 12, 33, 23)
        )

        val scanRange = Log.createScanRange(filter, null)

        // Order is reversed for timestamp
        scanRange.start.toHex() shouldBe "0000000000000000000000"
        scanRange.startInclusive shouldBe true
        scanRange.end?.toHex() shouldBe "7fffffa3f445ec7fff0000"
        scanRange.endInclusive shouldBe false

        scanRange.keyBeforeStart(match.bytes) shouldBe false
        scanRange.keyOutOfRange(match.bytes) shouldBe true
        scanRange.matchesPartials(match.bytes) shouldBe true

        scanRange.keyBeforeStart(earlier.bytes) shouldBe false
        scanRange.keyOutOfRange(earlier.bytes) shouldBe false
        scanRange.matchesPartials(earlier.bytes) shouldBe true

        scanRange.keyBeforeStart(later.bytes) shouldBe false
        scanRange.keyOutOfRange(later.bytes) shouldBe true
        scanRange.matchesPartials(later.bytes) shouldBe true
    }

    @Test
    fun convertGreaterThanEqualsFilterToScanRange() {
        val filter = GreaterThanEquals(
            Log.ref { timestamp } with DateTime(2018, 12, 8, 12, 33, 23)
        )

        val scanRange = Log.createScanRange(filter, null)

        // Order is reversed for timestamp
        scanRange.start.toHex() shouldBe "0000000000000000000000"
        scanRange.startInclusive shouldBe true
        scanRange.end?.toHex() shouldBe "7fffffa3f445ec7fffffff"
        scanRange.endInclusive shouldBe true

        scanRange.keyBeforeStart(match.bytes) shouldBe false
        scanRange.keyOutOfRange(match.bytes) shouldBe false
        scanRange.matchesPartials(match.bytes) shouldBe true

        scanRange.keyBeforeStart(earlier.bytes) shouldBe false
        scanRange.keyOutOfRange(earlier.bytes) shouldBe false
        scanRange.matchesPartials(earlier.bytes) shouldBe true

        scanRange.keyBeforeStart(later.bytes) shouldBe false
        scanRange.keyOutOfRange(later.bytes) shouldBe true
        scanRange.matchesPartials(later.bytes) shouldBe true
    }

    @Test
    fun convertLessThanFilterToScanRange() {
        val filter = LessThan(
            Log.ref { timestamp } with DateTime(2018, 12, 8, 12, 33, 23)
        )

        val scanRange = Log.createScanRange(filter, null)

        // Order is reversed for timestamp
        scanRange.start.toHex() shouldBe "7fffffa3f445ec7fffffff"
        scanRange.startInclusive shouldBe false
        scanRange.end?.toHex() shouldBe "ffffffffffffffffffffff"
        scanRange.endInclusive shouldBe true

        scanRange.keyBeforeStart(match.bytes) shouldBe true
        scanRange.keyOutOfRange(match.bytes) shouldBe false
        scanRange.matchesPartials(match.bytes) shouldBe true

        scanRange.keyBeforeStart(earlier.bytes) shouldBe true
        scanRange.keyOutOfRange(earlier.bytes) shouldBe false
        scanRange.matchesPartials(earlier.bytes) shouldBe true

        scanRange.keyBeforeStart(later.bytes) shouldBe false
        scanRange.keyOutOfRange(later.bytes) shouldBe false
        scanRange.matchesPartials(later.bytes) shouldBe true
    }

    @Test
    fun convertLessThanEqualsFilterToScanRange() {
        val filter = LessThanEquals(
            Log.ref { timestamp } with DateTime(2018, 12, 8, 12, 33, 23)
        )

        val scanRange = Log.createScanRange(filter, null)

        // Order is reversed for timestamp
        scanRange.start.toHex() shouldBe "7fffffa3f445ec7fff0000"
        scanRange.startInclusive shouldBe true
        scanRange.end?.toHex() shouldBe "ffffffffffffffffffffff"
        scanRange.endInclusive shouldBe true

        scanRange.keyBeforeStart(match.bytes) shouldBe false
        scanRange.keyOutOfRange(match.bytes) shouldBe false
        scanRange.matchesPartials(match.bytes) shouldBe true

        scanRange.keyBeforeStart(earlier.bytes) shouldBe true
        scanRange.keyOutOfRange(earlier.bytes) shouldBe false
        scanRange.matchesPartials(earlier.bytes) shouldBe true

        scanRange.keyBeforeStart(later.bytes) shouldBe false
        scanRange.keyOutOfRange(later.bytes) shouldBe false
        scanRange.matchesPartials(later.bytes) shouldBe true
    }

    @Test
    fun convertRangeFilterToScanRange() {
        val filter = Range(
            Log.ref { timestamp } with DateTime(2018, 12, 8, 12, 33, 1, 1)..DateTime(2018, 12, 8, 12, 33, 55, 2)
        )

        val scanRange = Log.createScanRange(filter, null)

        scanRange.start.toHex() shouldBe "7fffffa3f445cc7ffd0000"
        scanRange.startInclusive shouldBe true
        scanRange.end?.toHex() shouldBe "7fffffa3f446027ffeffff"
        scanRange.endInclusive shouldBe true

        scanRange.keyBeforeStart(match.bytes) shouldBe false
        scanRange.keyOutOfRange(match.bytes) shouldBe false
        scanRange.matchesPartials(match.bytes) shouldBe true

        scanRange.keyBeforeStart(earlier.bytes) shouldBe true
        scanRange.keyOutOfRange(earlier.bytes) shouldBe false
        scanRange.matchesPartials(earlier.bytes) shouldBe true

        scanRange.keyBeforeStart(later.bytes) shouldBe false
        scanRange.keyOutOfRange(later.bytes) shouldBe true
        scanRange.matchesPartials(later.bytes) shouldBe true
    }

    @Test
    fun convertValueInFilterToScanRange() {
        val filter = ValueIn(
            Log.ref { timestamp } with setOf(
                DateTime(2018, 12, 8, 12, 1, 1, 1),
                DateTime(2018, 12, 8, 12, 2, 2, 2),
                DateTime(2018, 12, 8, 12, 3, 3, 3)
            )
        )

        val scanRange = Log.createScanRange(filter, null)

        scanRange.start.toHex() shouldBe "7fffffa3f44d087ffc0000"
        scanRange.startInclusive shouldBe true
        scanRange.end?.toHex() shouldBe "7fffffa3f44d827ffeffff"
        scanRange.endInclusive shouldBe true

        val match = Log.key(Log("message", ERROR, DateTime(2018, 12, 8, 12, 2, 2, 2)))

        scanRange.keyBeforeStart(match.bytes) shouldBe false
        scanRange.keyOutOfRange(match.bytes) shouldBe false
        scanRange.matchesPartials(match.bytes) shouldBe true

        scanRange.keyBeforeStart(earlier.bytes) shouldBe true
        scanRange.keyOutOfRange(earlier.bytes) shouldBe false
        scanRange.matchesPartials(earlier.bytes) shouldBe false

        scanRange.keyBeforeStart(later.bytes) shouldBe false
        scanRange.keyOutOfRange(later.bytes) shouldBe true
        scanRange.matchesPartials(later.bytes) shouldBe false
    }

    @Test
    fun convertAndFilterToScanRange() {
        val filter = And(
            Equals(
                Log.ref { timestamp } with DateTime(2018, 12, 8, 12, 33, 23)
            ),
            LessThan(
                Log.ref { severity } with ERROR
            )
        )

        val scanRange = Log.createScanRange(filter, null)

        scanRange.start.toHex() shouldBe "7fffffa3f445ec7fff0000"
        scanRange.startInclusive shouldBe true
        scanRange.end?.toHex() shouldBe "7fffffa3f445ec7fff0003"
        scanRange.endInclusive shouldBe false

        scanRange.keyBeforeStart(match.bytes) shouldBe false
        scanRange.keyOutOfRange(match.bytes) shouldBe true
        scanRange.matchesPartials(match.bytes) shouldBe true

        scanRange.keyBeforeStart(earlier.bytes) shouldBe true
        scanRange.keyOutOfRange(earlier.bytes) shouldBe false
        scanRange.matchesPartials(earlier.bytes) shouldBe true

        scanRange.keyBeforeStart(later.bytes) shouldBe false
        scanRange.keyOutOfRange(later.bytes) shouldBe true
        scanRange.matchesPartials(later.bytes) shouldBe true
    }

    @Test
    fun convertNoKeyPartsToScanRange() {
        val filter = LessThan(
            Log.ref { severity } with ERROR
        )

        val scanRange = Log.createScanRange(filter, null)

        scanRange.start.toHex() shouldBe "0000000000000000000000"
        scanRange.startInclusive shouldBe true
        scanRange.end?.toHex() shouldBe "ffffffffffffffffffffff"
        scanRange.endInclusive shouldBe true

        scanRange.keyBeforeStart(match.bytes) shouldBe false
        scanRange.keyOutOfRange(match.bytes) shouldBe false
        scanRange.matchesPartials(match.bytes) shouldBe false

        scanRange.keyBeforeStart(earlier.bytes) shouldBe false
        scanRange.keyOutOfRange(earlier.bytes) shouldBe false
        scanRange.matchesPartials(earlier.bytes) shouldBe true

        scanRange.keyBeforeStart(later.bytes) shouldBe false
        scanRange.keyOutOfRange(later.bytes) shouldBe false
        scanRange.matchesPartials(later.bytes) shouldBe true
    }
}
