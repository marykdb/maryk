package maryk.core.processors.datastore.scanRange

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import maryk.core.extensions.bytes.MAX_BYTE
import maryk.core.properties.definitions.index.Multiple
import maryk.core.properties.key
import maryk.core.properties.types.TypedValue
import maryk.core.query.filters.And
import maryk.core.query.filters.Equals
import maryk.core.query.filters.GreaterThan
import maryk.core.query.filters.GreaterThanEquals
import maryk.core.query.filters.LessThan
import maryk.core.query.filters.LessThanEquals
import maryk.core.query.filters.Prefix
import maryk.core.query.filters.Range
import maryk.core.query.filters.RegEx
import maryk.core.query.filters.ValueIn
import maryk.core.query.pairs.with
import maryk.lib.extensions.toHex
import maryk.test.models.CompleteMarykModel
import maryk.test.models.CompleteMarykModel.number
import maryk.test.models.CompleteMarykModel.time
import maryk.test.models.MarykEnumEmbedded.E1
import maryk.test.models.SimpleMarykTypeEnum.S1
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.expect

class IndexableScanRangesTest {
    private val keyScanRange = KeyScanRanges(
        ranges = listOf(
            ScanRange(
                start = ByteArray(23) { 0 },
                startInclusive = true,
                end = ByteArray(23) { MAX_BYTE },
                endInclusive = true
            )
        ),
        startKey = null,
        includeStart = true,
        equalPairs = listOf(),
        keySize = 23
    )

    private val indexable = Multiple(
        number.ref(),
        time.ref()
    )

    private val earlierDO = CompleteMarykModel(
        string = "Arend",
        number = 2u,
        time = LocalTime(12, 11, 10),
        booleanForKey = true,
        dateForKey = LocalDate(2019, 3, 20),
        multiForKey = TypedValue(S1, "test"),
        enumEmbedded = E1
    )
    private val earlierKey = CompleteMarykModel.key(earlierDO)
    private val earlierIndexValue = number.ref().toStorageByteArrayForIndex(
        earlierDO, earlierKey.bytes
    )!!

    private val matchDO = CompleteMarykModel(
        string = "Jannes",
        number = 5u,
        time = LocalTime(11, 10, 9),
        booleanForKey = true,
        dateForKey = LocalDate(2019, 3, 3),
        multiForKey = TypedValue(S1, "test"),
        enumEmbedded = E1
    )
    private val matchKey = CompleteMarykModel.key(matchDO)
    private val matchIndexValue = number.ref().toStorageByteArrayForIndex(
        matchDO, matchKey.bytes
    )!!

    private val laterDO = CompleteMarykModel(
        string = "Karel",
        number = 9u,
        time = LocalTime(9, 8, 7),
        booleanForKey = true,
        dateForKey = LocalDate(2019, 3, 1),
        multiForKey = TypedValue(S1, "test"),
        enumEmbedded = E1
    )
    private val laterKey = CompleteMarykModel.key(laterDO)
    private val laterIndexValue = number.ref().toStorageByteArrayForIndex(
        laterDO, laterKey.bytes
    )!!

    @Test
    fun convertSimpleEqualFilterToScanRange() {
        val filter = Equals(
            CompleteMarykModel { number::ref } with 5u
        )

        val scanRange = indexable.createScanRange(filter, keyScanRange)

        expect("00000005") { scanRange.ranges.first().start.toHex() }
        assertTrue { scanRange.ranges.first().startInclusive }
        expect("00000005") { scanRange.ranges.first().end?.toHex() }
        assertTrue { scanRange.ranges.first().endInclusive }

        assertFalse { scanRange.ranges.first().keyBeforeStart(matchIndexValue) }
        assertFalse { scanRange.ranges.first().keyOutOfRange(matchIndexValue) }
        assertTrue { scanRange.matchesPartials(matchIndexValue) }

        assertTrue { scanRange.ranges.first().keyBeforeStart(earlierIndexValue) }
        assertFalse { scanRange.ranges.first().keyOutOfRange(earlierIndexValue) }
        assertTrue { scanRange.matchesPartials(earlierIndexValue) }

        assertFalse { scanRange.ranges.first().keyBeforeStart(laterIndexValue) }
        assertTrue { scanRange.ranges.first().keyOutOfRange(laterIndexValue) }
        assertTrue { scanRange.matchesPartials(laterIndexValue) }
    }

    @Test
    fun convertGreaterThanFilterToScanRange() {
        val filter = GreaterThan(
            CompleteMarykModel { number::ref } with 5u
        )

        val scanRange = indexable.createScanRange(filter, keyScanRange)

        expect("00000005") { scanRange.ranges.first().start.toHex() }
        assertFalse { scanRange.ranges.first().startInclusive }
        expect("") { scanRange.ranges.first().end?.toHex() }
        assertTrue { scanRange.ranges.first().endInclusive }

        assertTrue { scanRange.ranges.first().keyBeforeStart(matchIndexValue) } // Because should skip
        assertFalse { scanRange.ranges.first().keyOutOfRange(matchIndexValue) }
        assertTrue { scanRange.matchesPartials(matchIndexValue) }

        assertTrue { scanRange.ranges.first().keyBeforeStart(earlierIndexValue) }
        assertFalse { scanRange.ranges.first().keyOutOfRange(earlierIndexValue) }
        assertTrue { scanRange.matchesPartials(earlierIndexValue) }

        assertFalse { scanRange.ranges.first().keyBeforeStart(laterIndexValue) }
        assertFalse { scanRange.ranges.first().keyOutOfRange(laterIndexValue) }
        assertTrue { scanRange.matchesPartials(laterIndexValue) }
    }

    @Test
    fun convertGreaterThanEqualsFilterToScanRange() {
        val filter = GreaterThanEquals(
            CompleteMarykModel { number::ref } with 5u
        )

        val scanRange = indexable.createScanRange(filter, keyScanRange)

        expect("00000005") { scanRange.ranges.first().start.toHex() }
        assertTrue { scanRange.ranges.first().startInclusive }
        expect("") { scanRange.ranges.first().end?.toHex() }
        assertTrue { scanRange.ranges.first().endInclusive }

        assertFalse { scanRange.ranges.first().keyBeforeStart(matchIndexValue) }
        assertFalse { scanRange.ranges.first().keyOutOfRange(matchIndexValue) }
        assertTrue { scanRange.matchesPartials(matchIndexValue) }

        assertTrue { scanRange.ranges.first().keyBeforeStart(earlierIndexValue) }
        assertFalse { scanRange.ranges.first().keyOutOfRange(earlierIndexValue) }
        assertTrue { scanRange.matchesPartials(earlierIndexValue) }

        assertFalse { scanRange.ranges.first().keyBeforeStart(laterIndexValue) }
        assertFalse { scanRange.ranges.first().keyOutOfRange(laterIndexValue) }
        assertTrue { scanRange.matchesPartials(laterIndexValue) }
    }

    @Test
    fun convertLessThanFilterToScanRange() {
        val filter = LessThan(
            CompleteMarykModel { number::ref } with 5u
        )

        val scanRange = indexable.createScanRange(filter, keyScanRange)

        expect("") { scanRange.ranges.first().start.toHex() }
        assertTrue { scanRange.ranges.first().startInclusive }
        expect("00000005") { scanRange.ranges.first().end?.toHex() }
        assertFalse { scanRange.ranges.first().endInclusive }

        assertFalse { scanRange.ranges.first().keyBeforeStart(matchIndexValue) }
        assertTrue { scanRange.ranges.first().keyOutOfRange(matchIndexValue) } // because should not be included
        assertTrue { scanRange.matchesPartials(matchIndexValue) }

        assertFalse { scanRange.ranges.first().keyBeforeStart(earlierIndexValue) }
        assertFalse { scanRange.ranges.first().keyOutOfRange(earlierIndexValue) }
        assertTrue { scanRange.matchesPartials(earlierIndexValue) }

        assertFalse { scanRange.ranges.first().keyBeforeStart(laterIndexValue) }
        assertTrue { scanRange.ranges.first().keyOutOfRange(laterIndexValue) }
        assertTrue { scanRange.matchesPartials(laterIndexValue) }
    }

    @Test
    fun convertLessThanEqualsFilterToScanRange() {
        val filter = LessThanEquals(
            CompleteMarykModel { number::ref } with 5u
        )

        val scanRange = indexable.createScanRange(filter, keyScanRange)

        expect("") { scanRange.ranges.first().start.toHex() }
        assertTrue { scanRange.ranges.first().startInclusive }
        expect("00000005") { scanRange.ranges.first().end?.toHex() }
        assertTrue { scanRange.ranges.first().endInclusive }

        assertFalse { scanRange.ranges.first().keyBeforeStart(matchIndexValue) }
        assertFalse { scanRange.ranges.first().keyOutOfRange(matchIndexValue) }
        assertTrue { scanRange.matchesPartials(matchIndexValue) }

        assertFalse { scanRange.ranges.first().keyBeforeStart(earlierIndexValue) }
        assertFalse { scanRange.ranges.first().keyOutOfRange(earlierIndexValue) }
        assertTrue { scanRange.matchesPartials(earlierIndexValue) }

        assertFalse { scanRange.ranges.first().keyBeforeStart(laterIndexValue) }
        assertTrue { scanRange.ranges.first().keyOutOfRange(laterIndexValue) }
        assertTrue { scanRange.matchesPartials(laterIndexValue) }
    }

    @Test
    fun convertRangeFilterToScanRange() {
        val filter = Range(
            CompleteMarykModel { number::ref } with 4u..6u
        )

        val scanRange = indexable.createScanRange(filter, keyScanRange)

        expect("00000004") { scanRange.ranges.first().start.toHex() }
        assertTrue { scanRange.ranges.first().startInclusive }
        expect("00000006") { scanRange.ranges.first().end?.toHex() }
        assertTrue { scanRange.ranges.first().endInclusive }

        assertFalse { scanRange.ranges.first().keyBeforeStart(matchIndexValue) }
        assertFalse { scanRange.ranges.first().keyOutOfRange(matchIndexValue) }
        assertTrue { scanRange.matchesPartials(matchIndexValue) }

        assertTrue { scanRange.ranges.first().keyBeforeStart(earlierIndexValue) }
        assertFalse { scanRange.ranges.first().keyOutOfRange(earlierIndexValue) }
        assertTrue { scanRange.matchesPartials(earlierIndexValue) }

        assertFalse { scanRange.ranges.first().keyBeforeStart(laterIndexValue) }
        assertTrue { scanRange.ranges.first().keyOutOfRange(laterIndexValue) }
        assertTrue { scanRange.matchesPartials(laterIndexValue) }
    }

    @Test
    fun convertValueInFilterToScanRange() {
        val filter = ValueIn(
            CompleteMarykModel { number::ref } with setOf(
                3u,
                5u,
                6u
            )
        )

        val scanRange = indexable.createScanRange(filter, keyScanRange)

        expect(3) { scanRange.ranges.size }

        expect("00000003") { scanRange.ranges.first().start.toHex() }
        assertTrue { scanRange.ranges.first().startInclusive }
        expect("00000003") { scanRange.ranges.first().end?.toHex() }
        assertTrue { scanRange.ranges.first().endInclusive }

        expect("00000005") { scanRange.ranges[1].start.toHex() }
        assertTrue { scanRange.ranges[1].startInclusive }
        expect("00000005") { scanRange.ranges[1].end?.toHex() }
        assertTrue { scanRange.ranges[1].endInclusive }

        expect("00000006") { scanRange.ranges.last().start.toHex() }
        assertTrue { scanRange.ranges.last().startInclusive }
        expect("00000006") { scanRange.ranges.last().end?.toHex() }
        assertTrue { scanRange.ranges.last().endInclusive }

        assertFalse { scanRange.ranges.first().keyBeforeStart(matchIndexValue) }
        assertTrue { scanRange.ranges.first().keyOutOfRange(matchIndexValue) }
        assertFalse { scanRange.ranges[1].keyBeforeStart(matchIndexValue) }
        assertFalse { scanRange.ranges[1].keyOutOfRange(matchIndexValue) }
        assertTrue { scanRange.ranges.last().keyBeforeStart(matchIndexValue) }
        assertFalse { scanRange.ranges.last().keyOutOfRange(matchIndexValue) }
        assertTrue { scanRange.matchesPartials(matchIndexValue) }

        assertTrue { scanRange.ranges.first().keyBeforeStart(earlierIndexValue) }
        assertFalse { scanRange.ranges.last().keyOutOfRange(earlierIndexValue) }
        assertFalse { scanRange.matchesPartials(earlierIndexValue) }

        assertFalse { scanRange.ranges.first().keyBeforeStart(laterIndexValue) }
        assertTrue { scanRange.ranges.last().keyOutOfRange(laterIndexValue) }
        assertFalse { scanRange.matchesPartials(laterIndexValue) }
    }

    @Test
    fun convertAndFilterToScanRange() {
        val filter = And(
            Equals(
                CompleteMarykModel { number::ref } with 5u
            ),
            LessThan(
                CompleteMarykModel { time::ref } with LocalTime(12, 11, 10)
            )
        )

        val scanRange = indexable.createScanRange(filter, keyScanRange)

        expect("00000005") { scanRange.ranges.first().start.toHex() }
        assertTrue { scanRange.ranges.first().startInclusive }
        expect("00000005029d6730") { scanRange.ranges.first().end?.toHex() }
        assertFalse { scanRange.ranges.first().endInclusive }

        assertFalse { scanRange.ranges.first().keyBeforeStart(matchIndexValue) }
        assertTrue { scanRange.ranges.first().keyOutOfRange(matchIndexValue) }
        assertTrue { scanRange.matchesPartials(matchIndexValue) }

        assertTrue { scanRange.ranges.first().keyBeforeStart(earlierIndexValue) }
        assertFalse { scanRange.ranges.first().keyOutOfRange(earlierIndexValue) }
        assertTrue { scanRange.matchesPartials(earlierIndexValue) }

        assertFalse { scanRange.ranges.first().keyBeforeStart(laterIndexValue) }
        assertTrue { scanRange.ranges.first().keyOutOfRange(laterIndexValue) }
        assertTrue { scanRange.matchesPartials(laterIndexValue) }
    }

    @Test
    fun convertNoKeyPartsToScanRange() {
        val filter = LessThan(
            CompleteMarykModel { boolean::ref } with true
        )

        val scanRange = indexable.createScanRange(filter, keyScanRange)

        expect("") { scanRange.ranges.first().start.toHex() }
        expect("") { scanRange.ranges.first().end?.toHex() }

        assertFalse { scanRange.ranges.first().keyBeforeStart(matchIndexValue) }
        assertFalse { scanRange.ranges.first().keyOutOfRange(matchIndexValue) }
        assertTrue { scanRange.matchesPartials(matchIndexValue) }

        assertFalse { scanRange.ranges.first().keyBeforeStart(earlierIndexValue) }
        assertFalse { scanRange.ranges.first().keyOutOfRange(earlierIndexValue) }
        assertTrue { scanRange.matchesPartials(earlierIndexValue) }

        assertFalse { scanRange.ranges.first().keyBeforeStart(laterIndexValue) }
        assertFalse { scanRange.ranges.first().keyOutOfRange(laterIndexValue) }
        assertTrue { scanRange.matchesPartials(laterIndexValue) }
    }

    @Test
    fun convertPrefixFilterToScanRange() {
        val filter = Prefix(
            CompleteMarykModel { string::ref } with "Jan"
        )

        val scanRange = CompleteMarykModel.string.ref().createScanRange(filter, keyScanRange)

        expect("4a616e") { scanRange.ranges.first().start.toHex() }
        expect("4a616e") { scanRange.ranges.first().end?.toHex() }

        val matchStringIndexValue = CompleteMarykModel.string.ref().toStorageByteArrayForIndex(
            matchDO, matchKey.bytes
        )!!

        assertFalse { scanRange.ranges.first().keyBeforeStart(matchStringIndexValue) }
        assertFalse { scanRange.ranges.first().keyOutOfRange(matchStringIndexValue) }
        assertTrue { scanRange.matchesPartials(matchStringIndexValue) }

        val earlierStringIndexValue = CompleteMarykModel.string.ref().toStorageByteArrayForIndex(
            earlierDO, earlierKey.bytes
        )!!

        assertTrue { scanRange.ranges.first().keyBeforeStart(earlierStringIndexValue) }
        assertFalse { scanRange.ranges.first().keyOutOfRange(earlierStringIndexValue) }
        assertTrue { scanRange.matchesPartials(earlierStringIndexValue) }

        val laterStringIndexValue = CompleteMarykModel.string.ref().toStorageByteArrayForIndex(
            laterDO, laterKey.bytes
        )!!

        assertFalse { scanRange.ranges.first().keyBeforeStart(laterStringIndexValue) }
        assertTrue { scanRange.ranges.first().keyOutOfRange(laterStringIndexValue) }
        assertTrue { scanRange.matchesPartials(laterStringIndexValue) }
    }

    @Test
    fun convertRegexFilterToScanRange() {
        val filter = RegEx(
            CompleteMarykModel { string::ref} with Regex("^[A-Z]an.*$")
        )

        val scanRange = CompleteMarykModel.string.ref().createScanRange(filter, keyScanRange)

        expect("") { scanRange.ranges.first().start.toHex() }
        expect("") { scanRange.ranges.first().end?.toHex() }

        val matchStringIndexValue = CompleteMarykModel.string.ref().toStorageByteArrayForIndex(
            matchDO, matchKey.bytes
        )!!

        assertFalse { scanRange.ranges.first().keyBeforeStart(matchStringIndexValue) }
        assertFalse { scanRange.ranges.first().keyOutOfRange(matchStringIndexValue) }
        // Only one to match with the RegEx
        assertTrue { scanRange.matchesPartials(matchStringIndexValue) }

        val earlierStringIndexValue = CompleteMarykModel.string.ref().toStorageByteArrayForIndex(
            earlierDO, earlierKey.bytes
        )!!

        assertFalse { scanRange.ranges.first().keyBeforeStart(earlierStringIndexValue) }
        assertFalse { scanRange.ranges.first().keyOutOfRange(earlierStringIndexValue) }
        assertFalse { scanRange.matchesPartials(earlierStringIndexValue) }

        val laterStringIndexValue = CompleteMarykModel.string.ref().toStorageByteArrayForIndex(
            laterDO, laterKey.bytes
        )!!

        assertFalse { scanRange.ranges.first().keyBeforeStart(laterStringIndexValue) }
        assertFalse { scanRange.ranges.first().keyOutOfRange(laterStringIndexValue) }
        assertFalse { scanRange.matchesPartials(laterStringIndexValue) }
    }
}
