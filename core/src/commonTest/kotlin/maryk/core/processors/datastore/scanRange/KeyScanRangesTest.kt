package maryk.core.processors.datastore.scanRange

import kotlinx.datetime.LocalDateTime
import maryk.core.models.key
import maryk.core.processors.datastore.matchers.IndexPartialToMatch
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
import maryk.test.models.Log
import maryk.test.models.Severity.DEBUG
import maryk.test.models.Severity.ERROR
import maryk.test.models.Severity.INFO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.expect

class KeyScanRangesTest {
    private val scanRange = KeyScanRanges(
        ranges = listOf(
            ScanRange(
                start = byteArrayOf(1, 2, 3, 4, 5),
                startInclusive = true,
                end = byteArrayOf(9, 8, 7, 6, 5),
                endInclusive = true
            )
        ),
        partialMatches = listOf(
            IndexPartialToMatch(1, 1, 5, byteArrayOf(2, 4)),
            IndexPartialToMatch(2, 3, 5, byteArrayOf(5, 6))
        ),
        equalPairs = listOf(),
        uniques = listOf(),
        startKey = null,
        includeStart = true,
        keySize = 5,
        equalBytes = 0u,
    )

    @Test
    fun keyOutOfRange() {
        assertFalse { scanRange.ranges.first().keyOutOfRange(byteArrayOf(3, 4, 5, 6, 7)) }
        assertTrue { scanRange.ranges.first().keyOutOfRange(byteArrayOf(9, 9, 8, 7, 6)) }
    }

    @Test
    fun keyMatches() {
        assertTrue { scanRange.matchesPartials(byteArrayOf(3, 2, 4, 5, 6)) }
        assertFalse { scanRange.matchesPartials(byteArrayOf(3, 4, 4, 5, 6)) }
        assertFalse { scanRange.matchesPartials(byteArrayOf(3, 2, 4, 6, 6)) }
    }

    private val match = Log.key(Log("message", ERROR, LocalDateTime(2018, 12, 8, 12, 33, 23)))
    // Dont be confused that the time is reversed. Later is referring to it is later in table, not in time.
    private val earlier = Log.key(Log("message", DEBUG, LocalDateTime(2019, 12, 8, 12, 33, 23)))
    private val later = Log.key(Log("message", INFO, LocalDateTime(2017, 12, 8, 12, 33, 23)))

    @Test
    fun convertSimpleEqualFilterToScanRange() {
        val filter = Equals(
            Log { timestamp::ref } with LocalDateTime(2018, 12, 8, 12, 33, 23)
        )

        val scanRange = Log.createScanRange(filter, null)

        assertEquals(9u, scanRange.equalBytes)

        expect("7fffffa3f445ec7fff0000") { scanRange.ranges.first().start.toHex() }
        assertTrue { scanRange.ranges.first().startInclusive }
        expect("7fffffa3f445ec7fffffff") { scanRange.ranges.first().end?.toHex() }
        assertTrue { scanRange.ranges.first().endInclusive }

        assertFalse { scanRange.ranges.first().keyBeforeStart(match.bytes) }
        assertFalse { scanRange.ranges.first().keyOutOfRange(match.bytes) }
        assertTrue { scanRange.matchesPartials(match.bytes) }

        assertTrue { scanRange.ranges.first().keyBeforeStart(earlier.bytes) }
        assertFalse { scanRange.ranges.first().keyOutOfRange(earlier.bytes) }
        assertTrue { scanRange.matchesPartials(earlier.bytes) }

        assertFalse { scanRange.ranges.first().keyBeforeStart(later.bytes) }
        assertTrue { scanRange.ranges.first().keyOutOfRange(later.bytes) }
        assertTrue { scanRange.matchesPartials(later.bytes) }
    }

    @Test
    fun convertEqualFilterAndStartKeyToScanRange() {
        val log = Log(
            timestamp = LocalDateTime(2018, 12, 8, 12, 33, 23),
            severity = ERROR,
            message = "message 1"
        )

        val logKey = Log.key(log)

        val filter = Equals(
            Log { timestamp::ref } with LocalDateTime(2018, 12, 8, 12, 33, 23)
        )

        val scanRange1 = Log.createScanRange(filter, logKey.bytes, true)

        assertEquals(9u, scanRange1.equalBytes)

        expect("7fffffa3f445ec7fff0003") { scanRange1.startKey!!.toHex() }
        assertTrue(scanRange1.includeStart)

        expect("7fffffa3f445ec7fff0000") { scanRange1.ranges.first().start.toHex() }
        assertEquals(1, scanRange1.ranges.count())
        assertTrue { scanRange1.ranges.first().startInclusive }
        expect("7fffffa3f445ec7fffffff") { scanRange1.ranges.first().end?.toHex() }
        assertTrue { scanRange1.ranges.first().endInclusive }

        val scanRange2 = Log.createScanRange(filter, logKey.bytes, false)

        expect("7fffffa3f445ec7fff0003") { scanRange2.startKey!!.toHex() }
        assertFalse(scanRange2.includeStart)

        expect("7fffffa3f445ec7fff0000") { scanRange2.ranges.first().start.toHex() }
        assertEquals(1, scanRange2.ranges.count())
        assertTrue { scanRange2.ranges.first().startInclusive }
        expect("7fffffa3f445ec7fffffff") { scanRange2.ranges.first().end?.toHex() }
        assertTrue { scanRange2.ranges.first().endInclusive }

        // start key should be excluded when includeStart is false
        assertFalse { scanRange1.keyBeforeStart(logKey.bytes) }
        assertTrue { scanRange2.keyBeforeStart(logKey.bytes) }
    }

    @Test
    fun convertGreaterThanFilterToScanRange() {
        val filter = GreaterThan(
            Log { timestamp::ref} with LocalDateTime(2018, 12, 8, 12, 33, 23)
        )

        val scanRange = Log.createScanRange(filter, null)

        assertEquals(0u, scanRange.equalBytes)

        // Order is reversed for timestamp
        expect("0000000000000000000000") { scanRange.ranges.first().start.toHex() }
        assertTrue { scanRange.ranges.first().startInclusive }
        expect("7fffffa3f445ec7fff0000") { scanRange.ranges.first().end?.toHex() }
        assertFalse { scanRange.ranges.first().endInclusive }

        assertFalse { scanRange.ranges.first().keyBeforeStart(match.bytes) }
        assertTrue { scanRange.ranges.first().keyOutOfRange(match.bytes) }
        assertTrue { scanRange.matchesPartials(match.bytes) }

        assertFalse { scanRange.ranges.first().keyBeforeStart(earlier.bytes) }
        assertFalse { scanRange.ranges.first().keyOutOfRange(earlier.bytes) }
        assertTrue { scanRange.matchesPartials(earlier.bytes) }

        assertFalse { scanRange.ranges.first().keyBeforeStart(later.bytes) }
        assertTrue { scanRange.ranges.first().keyOutOfRange(later.bytes) }
        assertTrue { scanRange.matchesPartials(later.bytes) }
    }

    @Test
    fun convertGreaterThanEqualsFilterToScanRange() {
        val filter = GreaterThanEquals(
            Log { timestamp::ref } with LocalDateTime(2018, 12, 8, 12, 33, 23)
        )

        val scanRange = Log.createScanRange(filter, null)

        assertEquals(0u, scanRange.equalBytes)

        // Order is reversed for timestamp
        expect("0000000000000000000000") { scanRange.ranges.first().start.toHex() }
        assertTrue { scanRange.ranges.first().startInclusive }
        expect("7fffffa3f445ec7fffffff") { scanRange.ranges.first().end?.toHex() }
        assertTrue { scanRange.ranges.first().endInclusive }

        assertFalse { scanRange.ranges.first().keyBeforeStart(match.bytes) }
        assertFalse { scanRange.ranges.first().keyOutOfRange(match.bytes) }
        assertTrue { scanRange.matchesPartials(match.bytes) }

        assertFalse { scanRange.ranges.first().keyBeforeStart(earlier.bytes) }
        assertFalse { scanRange.ranges.first().keyOutOfRange(earlier.bytes) }
        assertTrue { scanRange.matchesPartials(earlier.bytes) }

        assertFalse { scanRange.ranges.first().keyBeforeStart(later.bytes) }
        assertTrue { scanRange.ranges.first().keyOutOfRange(later.bytes) }
        assertTrue { scanRange.matchesPartials(later.bytes) }
    }

    @Test
    fun convertLessThanFilterToScanRange() {
        val filter = LessThan(
            Log { timestamp::ref } with LocalDateTime(2018, 12, 8, 12, 33, 23)
        )

        val scanRange = Log.createScanRange(filter, null)

        assertEquals(0u, scanRange.equalBytes)

        // Order is reversed for timestamp
        expect("7fffffa3f445ec7fffffff") { scanRange.ranges.first().start.toHex() }
        assertFalse { scanRange.ranges.first().startInclusive }
        expect("ffffffffffffffffffffff") { scanRange.ranges.first().end?.toHex() }
        assertTrue { scanRange.ranges.first().endInclusive }

        assertTrue { scanRange.ranges.first().keyBeforeStart(match.bytes) }
        assertFalse { scanRange.ranges.first().keyOutOfRange(match.bytes) }
        assertTrue { scanRange.matchesPartials(match.bytes) }

        assertTrue { scanRange.ranges.first().keyBeforeStart(earlier.bytes) }
        assertFalse { scanRange.ranges.first().keyOutOfRange(earlier.bytes) }
        assertTrue { scanRange.matchesPartials(earlier.bytes) }

        assertFalse { scanRange.ranges.first().keyBeforeStart(later.bytes) }
        assertFalse { scanRange.ranges.first().keyOutOfRange(later.bytes) }
        assertTrue { scanRange.matchesPartials(later.bytes) }
    }

    @Test
    fun convertLessThanEqualsFilterToScanRange() {
        val filter = LessThanEquals(
            Log { timestamp::ref } with LocalDateTime(2018, 12, 8, 12, 33, 23)
        )

        val scanRange = Log.createScanRange(filter, null)

        assertEquals(0u, scanRange.equalBytes)

        // Order is reversed for timestamp
        expect("7fffffa3f445ec7fff0000") { scanRange.ranges.first().start.toHex() }
        assertTrue { scanRange.ranges.first().startInclusive }
        expect("ffffffffffffffffffffff") { scanRange.ranges.first().end?.toHex() }
        assertTrue { scanRange.ranges.first().endInclusive }

        assertFalse { scanRange.ranges.first().keyBeforeStart(match.bytes) }
        assertFalse { scanRange.ranges.first().keyOutOfRange(match.bytes) }
        assertTrue { scanRange.matchesPartials(match.bytes) }

        assertTrue { scanRange.ranges.first().keyBeforeStart(earlier.bytes) }
        assertFalse { scanRange.ranges.first().keyOutOfRange(earlier.bytes) }
        assertTrue { scanRange.matchesPartials(earlier.bytes) }

        assertFalse { scanRange.ranges.first().keyBeforeStart(later.bytes) }
        assertFalse { scanRange.ranges.first().keyOutOfRange(later.bytes) }
        assertTrue { scanRange.matchesPartials(later.bytes) }
    }

    @Test
    fun convertRangeFilterToScanRange() {
        val filter = Range(
            Log { timestamp::ref } with LocalDateTime(2018, 12, 8, 12, 33, 1, 1000000)..LocalDateTime(2018, 12, 8, 12, 33, 55, 2000000)
        )

        val scanRange = Log.createScanRange(filter, null)

        assertEquals(5u, scanRange.equalBytes)

        expect("7fffffa3f445cc7ffd0000") { scanRange.ranges.first().start.toHex() }
        assertTrue { scanRange.ranges.first().startInclusive }
        expect("7fffffa3f446027ffeffff") { scanRange.ranges.first().end?.toHex() }
        assertTrue { scanRange.ranges.first().endInclusive }

        assertFalse { scanRange.ranges.first().keyBeforeStart(match.bytes) }
        assertFalse { scanRange.ranges.first().keyOutOfRange(match.bytes) }
        assertTrue { scanRange.matchesPartials(match.bytes) }

        assertTrue { scanRange.ranges.first().keyBeforeStart(earlier.bytes) }
        assertFalse { scanRange.ranges.first().keyOutOfRange(earlier.bytes) }
        assertTrue { scanRange.matchesPartials(earlier.bytes) }

        assertFalse { scanRange.ranges.first().keyBeforeStart(later.bytes) }
        assertTrue { scanRange.ranges.first().keyOutOfRange(later.bytes) }
        assertTrue { scanRange.matchesPartials(later.bytes) }
    }

    @Test
    fun convertValueInFilterToScanRange() {
        val filter = ValueIn(
            Log { timestamp::ref } with setOf(
                LocalDateTime(2018, 12, 8, 12, 1, 1, 1000000),
                LocalDateTime(2018, 12, 8, 12, 2, 2, 2000000),
                LocalDateTime(2018, 12, 8, 12, 3, 3, 3000000)
            )
        )

        val scanRange = Log.createScanRange(filter, null)

        assertEquals(6u, scanRange.equalBytes)

        expect("7fffffa3f44d087ffc0000") { scanRange.ranges.first().start.toHex() }
        assertTrue { scanRange.ranges.first().startInclusive }
        expect("7fffffa3f44d087ffcffff") { scanRange.ranges.first().end?.toHex() }
        assertTrue { scanRange.ranges.first().endInclusive }

        expect("7fffffa3f44d827ffe0000") { scanRange.ranges.last().start.toHex() }
        assertTrue { scanRange.ranges.last().startInclusive }
        expect("7fffffa3f44d827ffeffff") { scanRange.ranges.last().end?.toHex() }
        assertTrue { scanRange.ranges.last().endInclusive }

        val match = Log.key(Log.create { message with "message"; severity with ERROR; timestamp with LocalDateTime(2018, 12, 8, 12, 2, 2, 2000000) })

        assertFalse { scanRange.ranges.first().keyBeforeStart(match.bytes) }
        assertTrue { scanRange.ranges.first().keyOutOfRange(match.bytes) }
        assertFalse { scanRange.ranges[1].keyBeforeStart(match.bytes) }
        assertFalse { scanRange.ranges[1].keyOutOfRange(match.bytes) }
        assertTrue { scanRange.ranges.last().keyBeforeStart(match.bytes) }
        assertFalse { scanRange.ranges.last().keyOutOfRange(match.bytes) }
        assertTrue { scanRange.matchesPartials(match.bytes) }

        assertTrue { scanRange.ranges.first().keyBeforeStart(earlier.bytes) }
        assertFalse { scanRange.ranges.first().keyOutOfRange(earlier.bytes) }
        assertTrue { scanRange.ranges.last().keyBeforeStart(earlier.bytes) }
        assertFalse { scanRange.ranges.last().keyOutOfRange(earlier.bytes) }
        assertFalse { scanRange.matchesPartials(earlier.bytes) }

        assertFalse { scanRange.ranges.first().keyBeforeStart(later.bytes) }
        assertTrue { scanRange.ranges.first().keyOutOfRange(later.bytes) }
        assertFalse { scanRange.matchesPartials(later.bytes) }
    }

    @Test
    fun convertEmptyValueInFilterToScanRange() {
        val filter = ValueIn(
            Log { timestamp::ref } with emptySet<LocalDateTime>()
        )

        val scanRange = Log.createScanRange(filter, null)

        assertEquals(0, scanRange.ranges.size)
    }

    @Test
    fun convertAndFilterToScanRange() {
        val filter = And(
            Equals(
                Log { timestamp::ref } with LocalDateTime(2018, 12, 8, 12, 33, 23)
            ),
            LessThan(
                Log { severity::ref } with ERROR
            )
        )

        val scanRange = Log.createScanRange(filter, null)

        assertEquals(10u, scanRange.equalBytes)

        expect("7fffffa3f445ec7fff0000") { scanRange.ranges.first().start.toHex() }
        assertTrue { scanRange.ranges.first().startInclusive }
        expect("7fffffa3f445ec7fff0003") { scanRange.ranges.first().end?.toHex() }
        assertFalse { scanRange.ranges.first().endInclusive }

        assertFalse { scanRange.ranges.first().keyBeforeStart(match.bytes) }
        assertTrue { scanRange.ranges.first().keyOutOfRange(match.bytes) }
        assertTrue { scanRange.matchesPartials(match.bytes) }

        assertTrue { scanRange.ranges.first().keyBeforeStart(earlier.bytes) }
        assertFalse { scanRange.ranges.first().keyOutOfRange(earlier.bytes) }
        assertTrue { scanRange.matchesPartials(earlier.bytes) }

        assertFalse { scanRange.ranges.first().keyBeforeStart(later.bytes) }
        assertTrue { scanRange.ranges.first().keyOutOfRange(later.bytes) }
        assertTrue { scanRange.matchesPartials(later.bytes) }
    }

    @Test
    fun convertNoKeyPartsToScanRange() {
        val filter = LessThan(
            Log { severity::ref } with ERROR
        )

        val scanRange = Log.createScanRange(filter, null)

        assertEquals(0u, scanRange.equalBytes)

        expect("0000000000000000000000") { scanRange.ranges.first().start.toHex() }
        assertTrue { scanRange.ranges.first().startInclusive }
        expect("ffffffffffffffffffffff") { scanRange.ranges.first().end?.toHex() }
        assertTrue { scanRange.ranges.first().endInclusive }

        assertFalse { scanRange.ranges.first().keyBeforeStart(match.bytes) }
        assertFalse { scanRange.ranges.first().keyOutOfRange(match.bytes) }
        assertFalse { scanRange.matchesPartials(match.bytes) }

        assertFalse { scanRange.ranges.first().keyBeforeStart(earlier.bytes) }
        assertFalse { scanRange.ranges.first().keyOutOfRange(earlier.bytes) }
        assertTrue { scanRange.matchesPartials(earlier.bytes) }

        assertFalse { scanRange.ranges.first().keyBeforeStart(later.bytes) }
        assertFalse { scanRange.ranges.first().keyOutOfRange(later.bytes) }
        assertTrue { scanRange.matchesPartials(later.bytes) }
    }
}
