package maryk.core.processors.datastore

import maryk.core.models.key
import maryk.core.properties.definitions.index.Multiple
import maryk.core.properties.types.TypedValue
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
import maryk.lib.time.Date
import maryk.lib.time.Time
import maryk.test.models.CompleteMarykModel
import maryk.test.models.CompleteMarykModel.Properties.number
import maryk.test.models.CompleteMarykModel.Properties.time
import maryk.test.models.MarykEnum.O1
import maryk.test.models.MarykEnumEmbedded.E1
import maryk.test.shouldBe
import kotlin.test.Test

class IndexableScanRangeTest {
    private val indexable = Multiple(
        number.ref(),
        time.ref()
    )

    private val earlierDO = CompleteMarykModel(
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

        val scanRange = indexable.createScanRange(filter, CompleteMarykModel.keyByteSize)

        scanRange.start.toHex() shouldBe "0000000501"
        scanRange.end?.toHex() shouldBe "0000000501"

        scanRange.keyBeforeStart(matchIndexValue) shouldBe false
        scanRange.keyOutOfRange(matchIndexValue) shouldBe false
        scanRange.keyMatches(matchIndexValue) shouldBe true

        scanRange.keyBeforeStart(earlierIndexValue) shouldBe true
        scanRange.keyOutOfRange(earlierIndexValue) shouldBe false
        scanRange.keyMatches(earlierIndexValue) shouldBe true

        scanRange.keyBeforeStart(laterIndexValue) shouldBe false
        scanRange.keyOutOfRange(laterIndexValue) shouldBe true
        scanRange.keyMatches(laterIndexValue) shouldBe true
    }

    @Test
    fun convertGreaterThanFilterToScanRange() {
        val filter = GreaterThan(
            CompleteMarykModel.ref { number } with 5u
        )

        val scanRange = indexable.createScanRange(filter, CompleteMarykModel.keyByteSize)

        scanRange.start.toHex() shouldBe "0000000502"
        scanRange.end?.toHex() shouldBe ""

        scanRange.keyBeforeStart(matchIndexValue) shouldBe true // Because should skip
        scanRange.keyOutOfRange(matchIndexValue) shouldBe false
        scanRange.keyMatches(matchIndexValue) shouldBe true

        scanRange.keyBeforeStart(earlierIndexValue) shouldBe true
        scanRange.keyOutOfRange(earlierIndexValue) shouldBe false
        scanRange.keyMatches(earlierIndexValue) shouldBe true

        scanRange.keyBeforeStart(laterIndexValue) shouldBe false
        scanRange.keyOutOfRange(laterIndexValue) shouldBe false
        scanRange.keyMatches(laterIndexValue) shouldBe true
    }

    @Test
    fun convertGreaterThanEqualsFilterToScanRange() {
        val filter = GreaterThanEquals(
            CompleteMarykModel.ref { number } with 5u
        )

        val scanRange = indexable.createScanRange(filter, CompleteMarykModel.keyByteSize)

        scanRange.start.toHex() shouldBe "0000000501"
        scanRange.end?.toHex() shouldBe ""

        scanRange.keyBeforeStart(matchIndexValue) shouldBe false
        scanRange.keyOutOfRange(matchIndexValue) shouldBe false
        scanRange.keyMatches(matchIndexValue) shouldBe true

        scanRange.keyBeforeStart(earlierIndexValue) shouldBe true
        scanRange.keyOutOfRange(earlierIndexValue) shouldBe false
        scanRange.keyMatches(earlierIndexValue) shouldBe true

        scanRange.keyBeforeStart(laterIndexValue) shouldBe false
        scanRange.keyOutOfRange(laterIndexValue) shouldBe false
        scanRange.keyMatches(laterIndexValue) shouldBe true
    }

    @Test
    fun convertLessThanFilterToScanRange() {
        val filter = LessThan(
            CompleteMarykModel.ref { number } with 5u
        )

        val scanRange = indexable.createScanRange(filter, CompleteMarykModel.keyByteSize)

        scanRange.start.toHex() shouldBe ""
        scanRange.end?.toHex() shouldBe "0000000500"

        scanRange.keyBeforeStart(matchIndexValue) shouldBe false
        scanRange.keyOutOfRange(matchIndexValue) shouldBe true // because should not be included
        scanRange.keyMatches(matchIndexValue) shouldBe true

        scanRange.keyBeforeStart(earlierIndexValue) shouldBe false
        scanRange.keyOutOfRange(earlierIndexValue) shouldBe false
        scanRange.keyMatches(earlierIndexValue) shouldBe true

        scanRange.keyBeforeStart(laterIndexValue) shouldBe false
        scanRange.keyOutOfRange(laterIndexValue) shouldBe true
        scanRange.keyMatches(laterIndexValue) shouldBe true
    }

    @Test
    fun convertLessThanEqualsFilterToScanRange() {
        val filter = LessThanEquals(
            CompleteMarykModel.ref { number } with 5u
        )

        val scanRange = indexable.createScanRange(filter, CompleteMarykModel.keyByteSize)

        scanRange.start.toHex() shouldBe ""
        scanRange.end?.toHex() shouldBe "0000000501"

        scanRange.keyBeforeStart(matchIndexValue) shouldBe false
        scanRange.keyOutOfRange(matchIndexValue) shouldBe false
        scanRange.keyMatches(matchIndexValue) shouldBe true

        scanRange.keyBeforeStart(earlierIndexValue) shouldBe false
        scanRange.keyOutOfRange(earlierIndexValue) shouldBe false
        scanRange.keyMatches(earlierIndexValue) shouldBe true

        scanRange.keyBeforeStart(laterIndexValue) shouldBe false
        scanRange.keyOutOfRange(laterIndexValue) shouldBe true
        scanRange.keyMatches(laterIndexValue) shouldBe true
    }

    @Test
    fun convertRangeFilterToScanRange() {
        val filter = Range(
            CompleteMarykModel.ref { number } with 4u..6u
        )

        val scanRange = indexable.createScanRange(filter, CompleteMarykModel.keyByteSize)

        scanRange.start.toHex() shouldBe "0000000401"
        scanRange.end?.toHex() shouldBe "0000000601"

        scanRange.keyBeforeStart(matchIndexValue) shouldBe false
        scanRange.keyOutOfRange(matchIndexValue) shouldBe false
        scanRange.keyMatches(matchIndexValue) shouldBe true

        scanRange.keyBeforeStart(earlierIndexValue) shouldBe true
        scanRange.keyOutOfRange(earlierIndexValue) shouldBe false
        scanRange.keyMatches(earlierIndexValue) shouldBe true

        scanRange.keyBeforeStart(laterIndexValue) shouldBe false
        scanRange.keyOutOfRange(laterIndexValue) shouldBe true
        scanRange.keyMatches(laterIndexValue) shouldBe true
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

        val scanRange = indexable.createScanRange(filter, CompleteMarykModel.keyByteSize)

        scanRange.start.toHex() shouldBe "0000000301"
        scanRange.end?.toHex() shouldBe "0000000601"

        scanRange.keyBeforeStart(matchIndexValue) shouldBe false
        scanRange.keyOutOfRange(matchIndexValue) shouldBe false
        scanRange.keyMatches(matchIndexValue) shouldBe true

        scanRange.keyBeforeStart(earlierIndexValue) shouldBe true
        scanRange.keyOutOfRange(earlierIndexValue) shouldBe false
        scanRange.keyMatches(earlierIndexValue) shouldBe false

        scanRange.keyBeforeStart(laterIndexValue) shouldBe false
        scanRange.keyOutOfRange(laterIndexValue) shouldBe true
        scanRange.keyMatches(laterIndexValue) shouldBe false
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

        val scanRange = indexable.createScanRange(filter, CompleteMarykModel.keyByteSize)

        scanRange.start.toHex() shouldBe "0000000501"
        scanRange.end?.toHex() shouldBe "0000000501029d673000"

        scanRange.keyBeforeStart(matchIndexValue) shouldBe false
        scanRange.keyOutOfRange(matchIndexValue) shouldBe true
        scanRange.keyMatches(matchIndexValue) shouldBe true

        scanRange.keyBeforeStart(earlierIndexValue) shouldBe true
        scanRange.keyOutOfRange(earlierIndexValue) shouldBe false
        scanRange.keyMatches(earlierIndexValue) shouldBe true

        scanRange.keyBeforeStart(laterIndexValue) shouldBe false
        scanRange.keyOutOfRange(laterIndexValue) shouldBe true
        scanRange.keyMatches(laterIndexValue) shouldBe true
    }

    @Test
    fun convertNoKeyPartsToScanRange() {
        val filter = LessThan(
            CompleteMarykModel.ref { boolean } with true
        )

        val scanRange = indexable.createScanRange(filter, CompleteMarykModel.keyByteSize)

        scanRange.start.toHex() shouldBe ""
        scanRange.end?.toHex() shouldBe ""

        scanRange.keyBeforeStart(matchIndexValue) shouldBe false
        scanRange.keyOutOfRange(matchIndexValue) shouldBe false
        scanRange.keyMatches(matchIndexValue) shouldBe true

        scanRange.keyBeforeStart(earlierIndexValue) shouldBe false
        scanRange.keyOutOfRange(earlierIndexValue) shouldBe false
        scanRange.keyMatches(earlierIndexValue) shouldBe true

        scanRange.keyBeforeStart(laterIndexValue) shouldBe false
        scanRange.keyOutOfRange(laterIndexValue) shouldBe false
        scanRange.keyMatches(laterIndexValue) shouldBe true
    }
}
