package maryk.core.processors.datastore

import maryk.core.extensions.bytes.MAX_BYTE
import maryk.core.models.key
import maryk.core.properties.definitions.index.Multiple
import maryk.core.properties.types.TypedValue
import maryk.core.query.filters.And
import maryk.core.query.filters.Equals
import maryk.core.query.filters.GreaterThan
import maryk.core.query.filters.GreaterThanEquals
import maryk.core.query.filters.LessThan
import maryk.core.query.filters.LessThanEquals
import maryk.core.query.filters.Prefix
import maryk.core.query.filters.Range
import maryk.core.query.filters.ValueIn
import maryk.core.query.pairs.with
import maryk.lib.extensions.toHex
import maryk.lib.time.Date
import maryk.lib.time.Time
import maryk.test.models.CompleteMarykModel
import maryk.test.models.CompleteMarykModel.Properties.number
import maryk.test.models.CompleteMarykModel.Properties.string
import maryk.test.models.CompleteMarykModel.Properties.time
import maryk.test.models.MarykEnum.O1
import maryk.test.models.MarykEnumEmbedded.E1
import maryk.test.shouldBe
import kotlin.test.Test

class IndexableScanRangeTest {
    private val keyScanRange = KeyScanRange(
        start = ByteArray(23) { 0 },
        startInclusive = true,
        end = ByteArray(23) { MAX_BYTE },
        endInclusive = true,
        equalPairs = listOf(),
        keySize = 23
    )

    private val indexable = Multiple(
        number.ref(),
        time.ref()
    )

    private val earlierDO = CompleteMarykModel(
        string = "Arend",
        number= 2u,
        time = Time(12, 11, 10),
        booleanForKey = true,
        dateForKey = Date(2019, 3, 20),
        multiForKey = TypedValue(O1, "test"),
        enumEmbedded = E1
    )
    private val earlierKey = CompleteMarykModel.key(earlierDO)
    private val earlierIndexValue = number.ref().toStorageByteArrayForIndex(
        earlierDO, earlierKey.bytes
    )!!

    private val matchDO = CompleteMarykModel(
        string = "Jannes",
        number= 5u,
        time = Time(11, 10, 9),
        booleanForKey = true,
        dateForKey = Date(2019, 3, 3),
        multiForKey = TypedValue(O1, "test"),
        enumEmbedded = E1
    )
    private val matchKey = CompleteMarykModel.key(matchDO)
    private val matchIndexValue = number.ref().toStorageByteArrayForIndex(
        matchDO, matchKey.bytes
    )!!

    private val laterDO = CompleteMarykModel(
        string = "Karel",
        number= 9u,
        time = Time(9, 8, 7),
        booleanForKey = true,
        dateForKey = Date(2019, 3, 1),
        multiForKey = TypedValue(O1, "test"),
        enumEmbedded = E1
    )
    private val laterKey = CompleteMarykModel.key(laterDO)
    private val laterIndexValue = number.ref().toStorageByteArrayForIndex(
        laterDO, laterKey.bytes
    )!!

    @Test
    fun convertSimpleEqualFilterToScanRange() {
        val filter = Equals(
            CompleteMarykModel.ref { number } with 5u
        )

        val scanRange = indexable.createScanRange(filter, keyScanRange)

        scanRange.start.toHex() shouldBe "00000005"
        scanRange.startInclusive shouldBe true
        scanRange.end?.toHex() shouldBe "00000005"
        scanRange.endInclusive shouldBe true

        scanRange.keyBeforeStart(matchIndexValue) shouldBe false
        scanRange.keyOutOfRange(matchIndexValue) shouldBe false
        scanRange.matchesPartials(matchIndexValue) shouldBe true

        scanRange.keyBeforeStart(earlierIndexValue) shouldBe true
        scanRange.keyOutOfRange(earlierIndexValue) shouldBe false
        scanRange.matchesPartials(earlierIndexValue) shouldBe true

        scanRange.keyBeforeStart(laterIndexValue) shouldBe false
        scanRange.keyOutOfRange(laterIndexValue) shouldBe true
        scanRange.matchesPartials(laterIndexValue) shouldBe true
    }

    @Test
    fun convertGreaterThanFilterToScanRange() {
        val filter = GreaterThan(
            CompleteMarykModel.ref { number } with 5u
        )

        val scanRange = indexable.createScanRange(filter, keyScanRange)

        scanRange.start.toHex() shouldBe "00000005"
        scanRange.startInclusive shouldBe false
        scanRange.end?.toHex() shouldBe ""
        scanRange.endInclusive shouldBe true

        scanRange.keyBeforeStart(matchIndexValue) shouldBe true // Because should skip
        scanRange.keyOutOfRange(matchIndexValue) shouldBe false
        scanRange.matchesPartials(matchIndexValue) shouldBe true

        scanRange.keyBeforeStart(earlierIndexValue) shouldBe true
        scanRange.keyOutOfRange(earlierIndexValue) shouldBe false
        scanRange.matchesPartials(earlierIndexValue) shouldBe true

        scanRange.keyBeforeStart(laterIndexValue) shouldBe false
        scanRange.keyOutOfRange(laterIndexValue) shouldBe false
        scanRange.matchesPartials(laterIndexValue) shouldBe true
    }

    @Test
    fun convertGreaterThanEqualsFilterToScanRange() {
        val filter = GreaterThanEquals(
            CompleteMarykModel.ref { number } with 5u
        )

        val scanRange = indexable.createScanRange(filter, keyScanRange)

        scanRange.start.toHex() shouldBe "00000005"
        scanRange.startInclusive shouldBe true
        scanRange.end?.toHex() shouldBe ""
        scanRange.endInclusive shouldBe true

        scanRange.keyBeforeStart(matchIndexValue) shouldBe false
        scanRange.keyOutOfRange(matchIndexValue) shouldBe false
        scanRange.matchesPartials(matchIndexValue) shouldBe true

        scanRange.keyBeforeStart(earlierIndexValue) shouldBe true
        scanRange.keyOutOfRange(earlierIndexValue) shouldBe false
        scanRange.matchesPartials(earlierIndexValue) shouldBe true

        scanRange.keyBeforeStart(laterIndexValue) shouldBe false
        scanRange.keyOutOfRange(laterIndexValue) shouldBe false
        scanRange.matchesPartials(laterIndexValue) shouldBe true
    }

    @Test
    fun convertLessThanFilterToScanRange() {
        val filter = LessThan(
            CompleteMarykModel.ref { number } with 5u
        )

        val scanRange = indexable.createScanRange(filter, keyScanRange)

        scanRange.start.toHex() shouldBe ""
        scanRange.startInclusive shouldBe true
        scanRange.end?.toHex() shouldBe "00000005"
        scanRange.endInclusive shouldBe false

        scanRange.keyBeforeStart(matchIndexValue) shouldBe false
        scanRange.keyOutOfRange(matchIndexValue) shouldBe true // because should not be included
        scanRange.matchesPartials(matchIndexValue) shouldBe true

        scanRange.keyBeforeStart(earlierIndexValue) shouldBe false
        scanRange.keyOutOfRange(earlierIndexValue) shouldBe false
        scanRange.matchesPartials(earlierIndexValue) shouldBe true

        scanRange.keyBeforeStart(laterIndexValue) shouldBe false
        scanRange.keyOutOfRange(laterIndexValue) shouldBe true
        scanRange.matchesPartials(laterIndexValue) shouldBe true
    }

    @Test
    fun convertLessThanEqualsFilterToScanRange() {
        val filter = LessThanEquals(
            CompleteMarykModel.ref { number } with 5u
        )

        val scanRange = indexable.createScanRange(filter, keyScanRange)

        scanRange.start.toHex() shouldBe ""
        scanRange.startInclusive shouldBe true
        scanRange.end?.toHex() shouldBe "00000005"
        scanRange.endInclusive shouldBe true

        scanRange.keyBeforeStart(matchIndexValue) shouldBe false
        scanRange.keyOutOfRange(matchIndexValue) shouldBe false
        scanRange.matchesPartials(matchIndexValue) shouldBe true

        scanRange.keyBeforeStart(earlierIndexValue) shouldBe false
        scanRange.keyOutOfRange(earlierIndexValue) shouldBe false
        scanRange.matchesPartials(earlierIndexValue) shouldBe true

        scanRange.keyBeforeStart(laterIndexValue) shouldBe false
        scanRange.keyOutOfRange(laterIndexValue) shouldBe true
        scanRange.matchesPartials(laterIndexValue) shouldBe true
    }

    @Test
    fun convertRangeFilterToScanRange() {
        val filter = Range(
            CompleteMarykModel.ref { number } with 4u..6u
        )

        val scanRange = indexable.createScanRange(filter, keyScanRange)

        scanRange.start.toHex() shouldBe "00000004"
        scanRange.startInclusive shouldBe true
        scanRange.end?.toHex() shouldBe "00000006"
        scanRange.endInclusive shouldBe true

        scanRange.keyBeforeStart(matchIndexValue) shouldBe false
        scanRange.keyOutOfRange(matchIndexValue) shouldBe false
        scanRange.matchesPartials(matchIndexValue) shouldBe true

        scanRange.keyBeforeStart(earlierIndexValue) shouldBe true
        scanRange.keyOutOfRange(earlierIndexValue) shouldBe false
        scanRange.matchesPartials(earlierIndexValue) shouldBe true

        scanRange.keyBeforeStart(laterIndexValue) shouldBe false
        scanRange.keyOutOfRange(laterIndexValue) shouldBe true
        scanRange.matchesPartials(laterIndexValue) shouldBe true
    }

    @Test
    fun convertValueInFilterToScanRange() {
        val filter = ValueIn(
            CompleteMarykModel.ref { number } with setOf(
                3u,
                5u,
                6u
            )
        )

        val scanRange = indexable.createScanRange(filter, keyScanRange)

        scanRange.start.toHex() shouldBe "00000003"
        scanRange.startInclusive shouldBe true
        scanRange.end?.toHex() shouldBe "00000006"
        scanRange.endInclusive shouldBe true

        scanRange.keyBeforeStart(matchIndexValue) shouldBe false
        scanRange.keyOutOfRange(matchIndexValue) shouldBe false
        scanRange.matchesPartials(matchIndexValue) shouldBe true

        scanRange.keyBeforeStart(earlierIndexValue) shouldBe true
        scanRange.keyOutOfRange(earlierIndexValue) shouldBe false
        scanRange.matchesPartials(earlierIndexValue) shouldBe false

        scanRange.keyBeforeStart(laterIndexValue) shouldBe false
        scanRange.keyOutOfRange(laterIndexValue) shouldBe true
        scanRange.matchesPartials(laterIndexValue) shouldBe false
    }

    @Test
    fun convertAndFilterToScanRange() {
        val filter = And(
            Equals(
                CompleteMarykModel.ref { number } with 5u
            ),
            LessThan(
                CompleteMarykModel.ref { time } with Time(12, 11, 10)
            )
        )

        val scanRange = indexable.createScanRange(filter, keyScanRange)

        scanRange.start.toHex() shouldBe "00000005"
        scanRange.startInclusive shouldBe true
        scanRange.end?.toHex() shouldBe "00000005029d6730"
        scanRange.endInclusive shouldBe false

        scanRange.keyBeforeStart(matchIndexValue) shouldBe false
        scanRange.keyOutOfRange(matchIndexValue) shouldBe true
        scanRange.matchesPartials(matchIndexValue) shouldBe true

        scanRange.keyBeforeStart(earlierIndexValue) shouldBe true
        scanRange.keyOutOfRange(earlierIndexValue) shouldBe false
        scanRange.matchesPartials(earlierIndexValue) shouldBe true

        scanRange.keyBeforeStart(laterIndexValue) shouldBe false
        scanRange.keyOutOfRange(laterIndexValue) shouldBe true
        scanRange.matchesPartials(laterIndexValue) shouldBe true
    }

    @Test
    fun convertNoKeyPartsToScanRange() {
        val filter = LessThan(
            CompleteMarykModel.ref { boolean } with true
        )

        val scanRange = indexable.createScanRange(filter, keyScanRange)

        scanRange.start.toHex() shouldBe ""
        scanRange.end?.toHex() shouldBe ""

        scanRange.keyBeforeStart(matchIndexValue) shouldBe false
        scanRange.keyOutOfRange(matchIndexValue) shouldBe false
        scanRange.matchesPartials(matchIndexValue) shouldBe true

        scanRange.keyBeforeStart(earlierIndexValue) shouldBe false
        scanRange.keyOutOfRange(earlierIndexValue) shouldBe false
        scanRange.matchesPartials(earlierIndexValue) shouldBe true

        scanRange.keyBeforeStart(laterIndexValue) shouldBe false
        scanRange.keyOutOfRange(laterIndexValue) shouldBe false
        scanRange.matchesPartials(laterIndexValue) shouldBe true
    }

    @Test
    fun convertPrefixFilterToScanRange() {
        val filter = Prefix(
            CompleteMarykModel.ref { string } with "Jan"
        )

        val scanRange = string.ref().createScanRange(filter, keyScanRange)

        scanRange.start.toHex() shouldBe "4a616e"
        scanRange.end?.toHex() shouldBe "4a616e"

        val matchStringIndexValue = string.ref().toStorageByteArrayForIndex(
            matchDO, matchKey.bytes
        )!!

        scanRange.keyBeforeStart(matchStringIndexValue) shouldBe false
        scanRange.keyOutOfRange(matchStringIndexValue) shouldBe false
        scanRange.matchesPartials(matchStringIndexValue) shouldBe true

        val earlierStringIndexValue = string.ref().toStorageByteArrayForIndex(
            earlierDO, earlierKey.bytes
        )!!

        scanRange.keyBeforeStart(earlierStringIndexValue) shouldBe true
        scanRange.keyOutOfRange(earlierStringIndexValue) shouldBe false
        scanRange.matchesPartials(earlierStringIndexValue) shouldBe true

        val laterStringIndexValue = string.ref().toStorageByteArrayForIndex(
            laterDO, laterKey.bytes
        )!!

        scanRange.keyBeforeStart(laterStringIndexValue) shouldBe false
        scanRange.keyOutOfRange(laterStringIndexValue) shouldBe true
        scanRange.matchesPartials(laterStringIndexValue) shouldBe true
    }
}
