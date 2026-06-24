package maryk.core.processors.datastore.scanRange

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import maryk.core.extensions.bytes.MAX_BYTE
import maryk.core.models.key
import maryk.core.processors.datastore.matchers.IndexPartialToMatch
import maryk.core.properties.definitions.index.Normalize
import maryk.core.properties.definitions.index.Multiple
import maryk.core.properties.types.invoke
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
import maryk.test.models.CompleteMarykModel
import maryk.test.models.CompleteMarykModel.number
import maryk.test.models.CompleteMarykModel.string
import maryk.test.models.CompleteMarykModel.time
import maryk.test.models.MarykEnumEmbedded.E1
import maryk.test.models.SimpleMarykTypeEnum.S1
import kotlin.test.Test
import kotlin.test.assertEquals
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
        keySize = 23,
        equalBytes = 0u,
    )

    private val indexable = Multiple(
        number.ref(),
        time.ref()
    )

    private val earlierDO = CompleteMarykModel.create {
        string with "Arend"
        number with 2u
        time with LocalTime(12, 11, 10)
        booleanForKey with true
        dateForKey with LocalDate(2019, 3, 20)
        multiForKey with S1( "test")
        enumEmbedded with E1
    }
    private val earlierKey = CompleteMarykModel.key(earlierDO)
    private val earlierIndexValue = number.ref().toStorageByteArrayForIndex(
        earlierDO, earlierKey.bytes
    )!!
    private val earlierCompositeIndexValue = indexable.toStorageByteArrayForIndex(
        earlierDO, earlierKey.bytes
    )!!
    private val earlierStringIndexValue = CompleteMarykModel.string.ref().toStorageByteArrayForIndex(
        earlierDO, earlierKey.bytes
    )!!
    private val earlierStringValueSize = CompleteMarykModel.string.ref().toStorageByteArrays(earlierDO).single().size

    private val matchDO = CompleteMarykModel.create {
        string with "Jannes"
        number with 5u
        time with LocalTime(11, 10, 9)
        booleanForKey with true
        dateForKey with LocalDate(2019, 3, 3)
        multiForKey with S1( "test")
        enumEmbedded with E1
    }
    private val matchKey = CompleteMarykModel.key(matchDO)
    private val matchIndexValue = number.ref().toStorageByteArrayForIndex(
        matchDO, matchKey.bytes
    )!!
    private val matchCompositeIndexValue = indexable.toStorageByteArrayForIndex(
        matchDO, matchKey.bytes
    )!!
    private val matchCompositeValueSize = indexable.toStorageByteArrays(matchDO).single().size
    private val numberValueSize = number.ref().toStorageByteArrays(matchDO).single().size
    private val matchStringIndexValue = CompleteMarykModel.string.ref().toStorageByteArrayForIndex(
        matchDO, matchKey.bytes
    )!!
    private val matchStringValueSize = CompleteMarykModel.string.ref().toStorageByteArrays(matchDO).single().size
    private val normalizedStringIndexable = Normalize(CompleteMarykModel.string.ref())
    private val matchNormalizedStringIndexValue = normalizedStringIndexable.toStorageByteArrayForIndex(
        matchDO, matchKey.bytes
    )!!
    private val matchNormalizedStringValueSize = normalizedStringIndexable.toStorageByteArrays(matchDO).single().size

    private val laterDO = CompleteMarykModel.create {
        string with "Karel"
        number with 9u
        time with LocalTime(9, 8, 7)
        booleanForKey with true
        dateForKey with LocalDate(2019, 3, 1)
        multiForKey with S1( "test")
        enumEmbedded with E1
    }
    private val laterKey = CompleteMarykModel.key(laterDO)
    private val laterIndexValue = number.ref().toStorageByteArrayForIndex(
        laterDO, laterKey.bytes
    )!!
    private val laterCompositeIndexValue = indexable.toStorageByteArrayForIndex(
        laterDO, laterKey.bytes
    )!!
    private val laterStringIndexValue = CompleteMarykModel.string.ref().toStorageByteArrayForIndex(
        laterDO, laterKey.bytes
    )!!
    private val laterStringValueSize = CompleteMarykModel.string.ref().toStorageByteArrays(laterDO).single().size

    @Test
    fun convertSimpleEqualFilterToScanRange() {
        val filter = Equals(
            CompleteMarykModel { number::ref } with 5u
        )

        val scanRange = indexable.createScanRange(filter, keyScanRange)

        expect("00000005") { scanRange.ranges.first().start.toHexString() }
        assertTrue { scanRange.ranges.first().startInclusive }
        expect("00000005") { scanRange.ranges.first().end?.toHexString() }
        assertTrue { scanRange.ranges.first().endInclusive }

        assertFalse { scanRange.ranges.first().keyBeforeStart(matchIndexValue, 0, numberValueSize) }
        assertFalse { scanRange.ranges.first().keyOutOfRange(matchIndexValue, 0, numberValueSize) }
        assertTrue { scanRange.matchesPartials(matchIndexValue, 0, numberValueSize, matchIndexValue.size) }

        assertTrue { scanRange.ranges.first().keyBeforeStart(earlierIndexValue, 0, numberValueSize) }
        assertFalse { scanRange.ranges.first().keyOutOfRange(earlierIndexValue, 0, numberValueSize) }
        assertTrue { scanRange.matchesPartials(earlierIndexValue, 0, numberValueSize, earlierIndexValue.size) }

        assertFalse { scanRange.ranges.first().keyBeforeStart(laterIndexValue, 0, numberValueSize) }
        assertTrue { scanRange.ranges.first().keyOutOfRange(laterIndexValue, 0, numberValueSize) }
        assertTrue { scanRange.matchesPartials(laterIndexValue, 0, numberValueSize, laterIndexValue.size) }
    }

    @Test
    fun exactEqualityRangeWithPartialMatchersExcludesLongerEqualPrefix() {
        val scanRange = IndexableScanRanges(
            ranges = listOf(
                ScanRange(
                    start = "garcia".encodeToByteArray(),
                    startInclusive = true,
                    end = "garcia".encodeToByteArray(),
                    endInclusive = true
                )
            ),
            partialMatches = listOf(
                IndexPartialToMatch(
                    indexableIndex = 0,
                    fromByteIndex = 0,
                    keySize = keyScanRange.keySize,
                    indexPartCount = 1,
                    toMatch = "gar".encodeToByteArray(),
                    partialMatch = true
                )
            ),
            keyScanRange = keyScanRange
        )

        val keySuffix = ByteArray(keyScanRange.keySize)
        val exact = "garcia".encodeToByteArray() + keySuffix
        val longerPrefix = "garcialopez".encodeToByteArray() + keySuffix

        assertTrue { scanRange.matchesPartials(exact, 0, exact.size - keySuffix.size, exact.size) }
        assertFalse { scanRange.matchesPartials(longerPrefix, 0, longerPrefix.size - keySuffix.size, longerPrefix.size) }
    }

    @Test
    fun convertGreaterThanFilterToScanRange() {
        val filter = GreaterThan(
            CompleteMarykModel { number::ref } with 5u
        )

        val scanRange = indexable.createScanRange(filter, keyScanRange)

        expect("00000005") { scanRange.ranges.first().start.toHexString() }
        assertFalse { scanRange.ranges.first().startInclusive }
        expect("") { scanRange.ranges.first().end?.toHexString() }
        assertTrue { scanRange.ranges.first().endInclusive }

        assertTrue { scanRange.ranges.first().keyBeforeStart(matchIndexValue, 0, numberValueSize) } // Because should skip
        assertFalse { scanRange.ranges.first().keyOutOfRange(matchIndexValue, 0, numberValueSize) }
        assertTrue { scanRange.matchesPartials(matchIndexValue, 0, numberValueSize, matchIndexValue.size) }

        assertTrue { scanRange.ranges.first().keyBeforeStart(earlierIndexValue, 0, numberValueSize) }
        assertFalse { scanRange.ranges.first().keyOutOfRange(earlierIndexValue, 0, numberValueSize) }
        assertTrue { scanRange.matchesPartials(earlierIndexValue) }

        assertFalse { scanRange.ranges.first().keyBeforeStart(laterIndexValue, 0, numberValueSize) }
        assertFalse { scanRange.ranges.first().keyOutOfRange(laterIndexValue, 0, numberValueSize) }
        assertTrue { scanRange.matchesPartials(laterIndexValue) }
    }

    @Test
    fun convertGreaterThanEqualsFilterToScanRange() {
        val filter = GreaterThanEquals(
            CompleteMarykModel { number::ref } with 5u
        )

        val scanRange = indexable.createScanRange(filter, keyScanRange)

        expect("00000005") { scanRange.ranges.first().start.toHexString() }
        assertTrue { scanRange.ranges.first().startInclusive }
        expect("") { scanRange.ranges.first().end?.toHexString() }
        assertTrue { scanRange.ranges.first().endInclusive }

        assertFalse { scanRange.ranges.first().keyBeforeStart(matchIndexValue) }
        assertFalse { scanRange.ranges.first().keyOutOfRange(matchIndexValue) }
        assertTrue { scanRange.matchesPartials(matchIndexValue, 0, numberValueSize, matchIndexValue.size) }

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

        expect("") { scanRange.ranges.first().start.toHexString() }
        assertTrue { scanRange.ranges.first().startInclusive }
        expect("00000005") { scanRange.ranges.first().end?.toHexString() }
        assertFalse { scanRange.ranges.first().endInclusive }

        assertFalse { scanRange.ranges.first().keyBeforeStart(matchIndexValue) }
        assertTrue { scanRange.ranges.first().keyOutOfRange(matchIndexValue) } // because should not be included
        assertTrue { scanRange.matchesPartials(matchIndexValue, 0, numberValueSize, matchIndexValue.size) }

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

        expect("") { scanRange.ranges.first().start.toHexString() }
        assertTrue { scanRange.ranges.first().startInclusive }
        expect("00000005") { scanRange.ranges.first().end?.toHexString() }
        assertTrue { scanRange.ranges.first().endInclusive }

        assertFalse { scanRange.ranges.first().keyBeforeStart(matchIndexValue, 0, numberValueSize) }
        assertFalse { scanRange.ranges.first().keyOutOfRange(matchIndexValue, 0, numberValueSize) }
        assertTrue { scanRange.matchesPartials(matchIndexValue, 0, numberValueSize, matchIndexValue.size) }

        assertFalse { scanRange.ranges.first().keyBeforeStart(earlierIndexValue, 0, numberValueSize) }
        assertFalse { scanRange.ranges.first().keyOutOfRange(earlierIndexValue, 0, numberValueSize) }
        assertTrue { scanRange.matchesPartials(earlierIndexValue) }

        assertFalse { scanRange.ranges.first().keyBeforeStart(laterIndexValue, 0, numberValueSize) }
        assertTrue { scanRange.ranges.first().keyOutOfRange(laterIndexValue, 0, numberValueSize) }
        assertTrue { scanRange.matchesPartials(laterIndexValue) }
    }

    @Test
    fun convertRangeFilterToScanRange() {
        val filter = Range(
            CompleteMarykModel { number::ref } with 4u..6u
        )

        val scanRange = indexable.createScanRange(filter, keyScanRange)

        expect("00000004") { scanRange.ranges.first().start.toHexString() }
        assertTrue { scanRange.ranges.first().startInclusive }
        expect("00000006") { scanRange.ranges.first().end?.toHexString() }
        assertTrue { scanRange.ranges.first().endInclusive }

        assertFalse { scanRange.ranges.first().keyBeforeStart(matchIndexValue) }
        assertFalse { scanRange.ranges.first().keyOutOfRange(matchIndexValue) }
        assertTrue { scanRange.matchesPartials(matchIndexValue, 0, numberValueSize, matchIndexValue.size) }

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

        expect("00000003") { scanRange.ranges.first().start.toHexString() }
        assertTrue { scanRange.ranges.first().startInclusive }
        expect("00000003") { scanRange.ranges.first().end?.toHexString() }
        assertTrue { scanRange.ranges.first().endInclusive }

        expect("00000005") { scanRange.ranges[1].start.toHexString() }
        assertTrue { scanRange.ranges[1].startInclusive }
        expect("00000005") { scanRange.ranges[1].end?.toHexString() }
        assertTrue { scanRange.ranges[1].endInclusive }

        expect("00000006") { scanRange.ranges.last().start.toHexString() }
        assertTrue { scanRange.ranges.last().startInclusive }
        expect("00000006") { scanRange.ranges.last().end?.toHexString() }
        assertTrue { scanRange.ranges.last().endInclusive }

        assertFalse { scanRange.ranges.first().keyBeforeStart(matchIndexValue, 0, numberValueSize) }
        assertTrue { scanRange.ranges.first().keyOutOfRange(matchIndexValue, 0, numberValueSize) }
        assertFalse { scanRange.ranges[1].keyBeforeStart(matchIndexValue, 0, numberValueSize) }
        assertFalse { scanRange.ranges[1].keyOutOfRange(matchIndexValue, 0, numberValueSize) }
        assertTrue { scanRange.ranges.last().keyBeforeStart(matchIndexValue, 0, numberValueSize) }
        assertFalse { scanRange.ranges.last().keyOutOfRange(matchIndexValue, 0, numberValueSize) }
        assertTrue { scanRange.matchesPartials(matchIndexValue, 0, numberValueSize, matchIndexValue.size) }

        assertTrue { scanRange.ranges.first().keyBeforeStart(earlierIndexValue, 0, numberValueSize) }
        assertFalse { scanRange.ranges.last().keyOutOfRange(earlierIndexValue, 0, numberValueSize) }
        assertFalse { scanRange.matchesPartials(earlierIndexValue, 0, numberValueSize, earlierIndexValue.size) }

        assertFalse { scanRange.ranges.first().keyBeforeStart(laterIndexValue, 0, numberValueSize) }
        assertTrue { scanRange.ranges.last().keyOutOfRange(laterIndexValue, 0, numberValueSize) }
        assertFalse { scanRange.matchesPartials(laterIndexValue, 0, numberValueSize, laterIndexValue.size) }
    }

    @Test
    fun convertMultipleValueInFiltersToScanRange() {
        val numbers = setOf(3u, 5u)
        val times = setOf(
            LocalTime(11, 10, 9),
            LocalTime(12, 11, 10)
        )
        val filter = And(
            ValueIn(CompleteMarykModel { number::ref } with numbers),
            ValueIn(CompleteMarykModel { time::ref } with times)
        )

        val scanRange = indexable.createScanRange(filter, keyScanRange)

        assertEquals(4, scanRange.ranges.size)

        for (numberValue in numbers) {
            for (timeValue in times) {
                val values = CompleteMarykModel.create {
                    string with "Jannes"
                    number with numberValue
                    time with timeValue
                    booleanForKey with true
                    dateForKey with LocalDate(2019, 3, 3)
                    multiForKey with S1("test")
                    enumEmbedded with E1
                }
                val key = CompleteMarykModel.key(values)
                val indexValue = indexable.toStorageByteArrayForIndex(values, key.bytes)!!

                assertTrue(
                    scanRange.ranges.any { range ->
                        !range.keyBeforeStart(indexValue, 0, matchCompositeValueSize) &&
                            !range.keyOutOfRange(indexValue, 0, matchCompositeValueSize)
                    }
                )
            }
        }
    }

    @Test
    fun convertEmptyValueInFilterToScanRange() {
        val filter = ValueIn(
            CompleteMarykModel { number::ref } with emptySet()
        )

        val scanRange = indexable.createScanRange(filter, keyScanRange)

        assertEquals(0, scanRange.ranges.size)
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

        expect("00000005") { scanRange.ranges.first().start.toHexString() }
        assertTrue { scanRange.ranges.first().startInclusive }
        expect("00000005029d6730") { scanRange.ranges.first().end?.toHexString() }
        assertFalse { scanRange.ranges.first().endInclusive }

        assertFalse { scanRange.ranges.first().keyBeforeStart(matchCompositeIndexValue, 0, matchCompositeValueSize) }
        assertFalse { scanRange.ranges.first().keyOutOfRange(matchCompositeIndexValue, 0, matchCompositeValueSize) }
        assertTrue { scanRange.matchesPartials(matchCompositeIndexValue, 0, matchCompositeValueSize, matchCompositeIndexValue.size) }

        assertTrue { scanRange.ranges.first().keyBeforeStart(earlierCompositeIndexValue, 0, matchCompositeValueSize) }
        assertFalse { scanRange.ranges.first().keyOutOfRange(earlierCompositeIndexValue, 0, matchCompositeValueSize) }
        assertTrue { scanRange.matchesPartials(earlierCompositeIndexValue, 0, matchCompositeValueSize, earlierCompositeIndexValue.size) }

        assertFalse { scanRange.ranges.first().keyBeforeStart(laterCompositeIndexValue, 0, matchCompositeValueSize) }
        assertTrue { scanRange.ranges.first().keyOutOfRange(laterCompositeIndexValue, 0, matchCompositeValueSize) }
        assertTrue { scanRange.matchesPartials(laterCompositeIndexValue, 0, matchCompositeValueSize, laterCompositeIndexValue.size) }
    }

    @Test
    fun convertNoKeyPartsToScanRange() {
        val filter = LessThan(
            CompleteMarykModel { boolean::ref } with true
        )

        val scanRange = indexable.createScanRange(filter, keyScanRange)

        expect("") { scanRange.ranges.first().start.toHexString() }
        expect("") { scanRange.ranges.first().end?.toHexString() }

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

        expect("4a616e") { scanRange.ranges.first().start.toHexString() }
        expect("4a616e") { scanRange.ranges.first().end?.toHexString() }

        assertFalse { scanRange.ranges.first().keyBeforeStart(matchStringIndexValue, 0, matchStringValueSize) }
        assertTrue { scanRange.ranges.first().keyOutOfRange(matchStringIndexValue, 0, matchStringValueSize) }
        assertTrue { scanRange.matchesPartials(matchStringIndexValue, 0, matchStringValueSize, matchStringIndexValue.size) }

        assertTrue { scanRange.ranges.first().keyBeforeStart(earlierStringIndexValue, 0, earlierStringValueSize) }
        assertFalse { scanRange.ranges.first().keyOutOfRange(earlierStringIndexValue, 0, earlierStringValueSize) }
        assertFalse { scanRange.matchesPartials(earlierStringIndexValue, 0, earlierStringValueSize, earlierStringIndexValue.size) }

        assertFalse { scanRange.ranges.first().keyBeforeStart(laterStringIndexValue, 0, laterStringValueSize) }
        assertTrue { scanRange.ranges.first().keyOutOfRange(laterStringIndexValue, 0, laterStringValueSize) }
        assertFalse { scanRange.matchesPartials(laterStringIndexValue, 0, laterStringValueSize, laterStringIndexValue.size) }
    }

    @Test
    fun convertPrefixFilterToNormalizeScanRange() {
        val filter = Prefix(
            CompleteMarykModel { string::ref } with " j-a n "
        )

        val scanRange = normalizedStringIndexable.createScanRange(filter, keyScanRange)

        expect("6a616e") { scanRange.ranges.first().start.toHexString() }
        expect("6a616e") { scanRange.ranges.first().end?.toHexString() }

        assertFalse { scanRange.ranges.first().keyBeforeStart(matchNormalizedStringIndexValue, 0, matchNormalizedStringValueSize) }
        assertTrue { scanRange.ranges.first().keyOutOfRange(matchNormalizedStringIndexValue, 0, matchNormalizedStringValueSize) }
        assertTrue { scanRange.matchesPartials(matchNormalizedStringIndexValue, 0, matchNormalizedStringValueSize, matchNormalizedStringIndexValue.size) }
    }

    @Test
    fun convertGreaterThanStringFilterToScanRange() {
        val filter = GreaterThan(
            CompleteMarykModel { string::ref } with "Jan"
        )

        val scanRange = CompleteMarykModel.string.ref().createScanRange(filter, keyScanRange)
        val matchStringIndexValue = CompleteMarykModel.string.ref().toStorageByteArrayForIndex(
            matchDO, matchKey.bytes
        )!!
        val matchStringValueSize = CompleteMarykModel.string.ref().toStorageByteArrays(matchDO).single().size

        assertFalse { scanRange.ranges.first().keyBeforeStart(matchStringIndexValue, 0, matchStringValueSize) }
        assertFalse { scanRange.ranges.first().keyOutOfRange(matchStringIndexValue, 0, matchStringValueSize) }
    }

    @Test
    fun convertLessThanStringFilterToScanRange() {
        val filter = LessThan(
            CompleteMarykModel { string::ref } with "Jannesz"
        )

        val scanRange = CompleteMarykModel.string.ref().createScanRange(filter, keyScanRange)
        val matchStringIndexValue = CompleteMarykModel.string.ref().toStorageByteArrayForIndex(
            matchDO, matchKey.bytes
        )!!
        val matchStringValueSize = CompleteMarykModel.string.ref().toStorageByteArrays(matchDO).single().size

        assertFalse { scanRange.ranges.first().keyBeforeStart(matchStringIndexValue, 0, matchStringValueSize) }
        assertFalse { scanRange.ranges.first().keyOutOfRange(matchStringIndexValue, 0, matchStringValueSize) }
    }

    @Test
    fun convertLessThanStringFilterToScanRangeRejectsLongerEqualPrefix() {
        val filter = LessThanEquals(
            CompleteMarykModel { string::ref } with "Jan"
        )

        val scanRange = CompleteMarykModel.string.ref().createScanRange(filter, keyScanRange)
        val matchStringIndexValue = CompleteMarykModel.string.ref().toStorageByteArrayForIndex(
            matchDO, matchKey.bytes
        )!!
        val matchStringValueSize = CompleteMarykModel.string.ref().toStorageByteArrays(matchDO).single().size

        assertFalse { scanRange.ranges.first().keyBeforeStart(matchStringIndexValue, 0, matchStringValueSize) }
        assertTrue { scanRange.ranges.first().keyOutOfRange(matchStringIndexValue, 0, matchStringValueSize) }
    }

    @Test
    fun convertRegexFilterToScanRange() {
        val filter = RegEx(
            CompleteMarykModel { string::ref} with Regex("^[A-Z]an.*$")
        )

        val scanRange = CompleteMarykModel.string.ref().createScanRange(filter, keyScanRange)

        expect("") { scanRange.ranges.first().start.toHexString() }
        expect("") { scanRange.ranges.first().end?.toHexString() }

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

    @Test
    fun matchesPartialsLimitsEmbeddedKeyToSourceEnd() {
        val key = byteArrayOf(9, 9)
        val scanRange = IndexableScanRanges(
            ranges = listOf(ScanRange(byteArrayOf(1), true, byteArrayOf(1), true)),
            keyScanRange = KeyScanRanges(
                ranges = listOf(ScanRange(key, true, key, true)),
                startKey = null,
                includeStart = true,
                equalPairs = emptyList(),
                keySize = key.size,
                equalBytes = 0u
            )
        )

        val indexValueWithHistoricVersion = byteArrayOf(1) + key + byteArrayOf(7, 7)

        assertTrue {
            scanRange.matchesPartials(
                indexValueWithHistoricVersion,
                offset = 0,
                length = 1,
                sourceEnd = 1 + key.size
            )
        }
    }

    @Test
    fun matchesPartialsReturnsFalseWhenSourceEndCannotContainKey() {
        val scanRange = IndexableScanRanges(
            ranges = listOf(ScanRange(byteArrayOf(1), true, byteArrayOf(1), true)),
            keyScanRange = KeyScanRanges(
                ranges = listOf(ScanRange(byteArrayOf(9, 9), true, byteArrayOf(9, 9), true)),
                startKey = null,
                includeStart = true,
                equalPairs = emptyList(),
                keySize = 2,
                equalBytes = 0u
            )
        )

        assertFalse {
            scanRange.matchesPartials(
                byteArrayOf(1),
                offset = 0,
                length = 1,
                sourceEnd = 1
            )
        }
    }
}
