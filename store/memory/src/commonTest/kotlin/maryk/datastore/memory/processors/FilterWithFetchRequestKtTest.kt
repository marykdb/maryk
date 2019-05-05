package maryk.datastore.memory.processors

import maryk.core.models.IsRootValuesDataModel
import maryk.core.models.key
import maryk.core.processors.datastore.writeToStorage
import maryk.core.properties.PropertyDefinitions
import maryk.core.query.filters.And
import maryk.core.query.filters.Equals
import maryk.core.query.filters.Exists
import maryk.core.query.filters.GreaterThan
import maryk.core.query.filters.GreaterThanEquals
import maryk.core.query.filters.LessThan
import maryk.core.query.filters.LessThanEquals
import maryk.core.query.filters.Not
import maryk.core.query.filters.Or
import maryk.core.query.filters.Prefix
import maryk.core.query.filters.Range
import maryk.core.query.filters.RegEx
import maryk.core.query.filters.ValueIn
import maryk.core.query.pairs.with
import maryk.core.values.Values
import maryk.datastore.memory.records.DataRecord
import maryk.datastore.memory.records.DataRecordValue
import maryk.lib.time.Date
import maryk.lib.time.DateTime
import maryk.lib.time.Time
import maryk.test.models.TestMarykModel
import maryk.test.shouldBe
import kotlin.test.Test

class FilterWithFetchRequestKtTest {
    private val value1 = TestMarykModel.createDataRecord(
        TestMarykModel(
            string = "haha1",
            int = 5,
            uint = 6u,
            double = 0.43,
            dateTime = DateTime(2018, 3, 2),
            bool = true,
            map = mapOf(
                Time(12, 13, 14) to "haha10"
            ),
            list = listOf(
                4, 6, 7
            ),
            set = setOf(
                Date(2019, 3, 30), Date(2018, 9, 9)
            )
        )
    )

    private fun <DM : IsRootValuesDataModel<P>, P : PropertyDefinitions> DM.createDataRecord(values: Values<DM, P>): DataRecord<DM, P> {
        val recordValues = mutableListOf<DataRecordValue<*>>()

        values.writeToStorage { _, reference, _, value ->
            recordValues += DataRecordValue(reference, value, 1234uL)
        }

        return DataRecord(
            key = this.key(values),
            firstVersion = 1234uL,
            lastVersion = 1234uL,
            values = recordValues
        )
    }

    @Test
    fun doExistsFilter() {
        filterMatches(
            Exists(TestMarykModel { string::ref }),
            value1,
            null
        ) shouldBe true

        // Below version it did not exist
        filterMatches(
            Exists(TestMarykModel { string::ref }),
            value1,
            1233uL
        ) shouldBe false

        filterMatches(
            Exists(TestMarykModel { reference::ref }),
            value1,
            null
        ) shouldBe false
    }

    @Test
    fun doEqualsFilter() {
        filterMatches(
            Equals(TestMarykModel { string::ref } with "haha1"),
            value1,
            null
        ) shouldBe true

        filterMatches(
            Equals(TestMarykModel { string::ref } with "haha1"),
            value1,
            1233uL
        ) shouldBe false

        filterMatches(
            Equals(TestMarykModel { string::ref } with "wrong"),
            value1,
            null
        ) shouldBe false
    }

    @Test
    fun doComplexMapListSetFilter() {
        filterMatches(
            Equals(TestMarykModel { map.refAt(Time(12, 13, 14)) } with "haha10"),
            value1,
            null
        ) shouldBe true

        filterMatches(
            Equals(TestMarykModel { map.refToAny() } with "haha10"),
            value1,
            null
        ) shouldBe true

        filterMatches(
            Equals(TestMarykModel { map.refToAny() } with "haha11"),
            value1,
            null
        ) shouldBe false

        filterMatches(
            Equals(TestMarykModel { map.refAt(Time(13, 13, 14)) } with "haha10"),
            value1,
            null
        ) shouldBe false

        filterMatches(
            Equals(TestMarykModel { list refAt 1u } with 6),
            value1,
            null
        ) shouldBe true

        filterMatches(
            Equals(TestMarykModel { list refAt 2u } with 6),
            value1,
            null
        ) shouldBe false

        filterMatches(
            Equals(TestMarykModel { list.refToAny() } with 6),
            value1,
            null
        ) shouldBe true

        filterMatches(
            Equals(TestMarykModel { list.refToAny() } with 2),
            value1,
            null
        ) shouldBe false

        filterMatches(
            Equals(TestMarykModel { list refAt 1u } with 6),
            value1,
            null
        ) shouldBe true

        filterMatches(
            Exists(TestMarykModel { set refAt Date(2018, 9, 9) }),
            value1,
            null
        ) shouldBe true

        filterMatches(
            Exists(TestMarykModel { set refAt Date(2017, 9, 9) }),
            value1,
            null
        ) shouldBe false
    }

    @Test
    fun doPrefixFilter() {
        filterMatches(
            Prefix(TestMarykModel { string::ref } with "ha"),
            value1,
            null
        ) shouldBe true

        filterMatches(
            Prefix(TestMarykModel { string::ref } with "wrong"),
            value1,
            null
        ) shouldBe false
    }

    @Test
    fun doLessThanFilter() {
        filterMatches(
            LessThan(TestMarykModel { int::ref } with 6),
            value1,
            null
        ) shouldBe true

        filterMatches(
            LessThan(TestMarykModel { int::ref } with 5),
            value1,
            null
        ) shouldBe false

        filterMatches(
            LessThan(TestMarykModel { int::ref } with 2),
            value1,
            null
        ) shouldBe false
    }

    @Test
    fun doLessThanEqualsFilter() {
        filterMatches(
            LessThanEquals(TestMarykModel { int::ref } with 6),
            value1,
            null
        ) shouldBe true

        filterMatches(
            LessThanEquals(TestMarykModel { int::ref } with 5),
            value1,
            null
        ) shouldBe true

        filterMatches(
            LessThanEquals(TestMarykModel { int::ref } with 2),
            value1,
            null
        ) shouldBe false
    }

    @Test
    fun doGreaterThanFilter() {
        filterMatches(
            GreaterThan(TestMarykModel { int::ref } with 4),
            value1,
            null
        ) shouldBe true

        filterMatches(
            GreaterThan(TestMarykModel { int::ref } with 5),
            value1,
            null
        ) shouldBe false

        filterMatches(
            GreaterThan(TestMarykModel { int::ref } with 6),
            value1,
            null
        ) shouldBe false
    }

    @Test
    fun doGreaterThanEqualsFilter() {
        filterMatches(
            GreaterThanEquals(TestMarykModel { int::ref } with 4),
            value1,
            null
        ) shouldBe true

        filterMatches(
            GreaterThanEquals(TestMarykModel { int::ref } with 5),
            value1,
            null
        ) shouldBe true

        filterMatches(
            GreaterThanEquals(TestMarykModel { int::ref } with 6),
            value1,
            null
        ) shouldBe false
    }

    @Test
    fun doRangeFilter() {
        filterMatches(
            Range(TestMarykModel { int::ref } with (2..8)),
            value1,
            null
        ) shouldBe true

        filterMatches(
            Range(TestMarykModel { int::ref } with (2..5)),
            value1,
            null
        ) shouldBe true

        filterMatches(
            Range(TestMarykModel { int::ref } with (2..3)),
            value1,
            null
        ) shouldBe false
    }

    @Test
    fun doRegExFilter() {
        filterMatches(
            RegEx(TestMarykModel { string::ref } with Regex("^h.*$")),
            value1,
            null
        ) shouldBe true

        filterMatches(
            RegEx(TestMarykModel { string::ref } with Regex("^b.*$")),
            value1,
            null
        ) shouldBe false
    }

    @Test
    fun doValueInFilter() {
        filterMatches(
            ValueIn(TestMarykModel { string::ref } with setOf("haha1", "haha2")),
            value1,
            null
        ) shouldBe true

        filterMatches(
            ValueIn(TestMarykModel { string::ref } with setOf("no1", "no2")),
            value1,
            null
        ) shouldBe false
    }

    @Test
    fun doNotFilter() {
        filterMatches(
            Not(Exists(TestMarykModel { string::ref })),
            value1,
            null
        ) shouldBe false

        filterMatches(
            Not(Exists(TestMarykModel { reference::ref })),
            value1,
            null
        ) shouldBe true
    }

    @Test
    fun doAndFilter() {
        filterMatches(
            And(
                Exists(TestMarykModel { int::ref }),
                Exists(TestMarykModel { string::ref })
            ),
            value1,
            null
        ) shouldBe true

        filterMatches(
            And(
                Exists(TestMarykModel { reference::ref }),
                Exists(TestMarykModel { string::ref })
            ),
            value1,
            null
        ) shouldBe false
    }

    @Test
    fun doOrFilter() {
        filterMatches(
            Or(
                Exists(TestMarykModel { int::ref }),
                Exists(TestMarykModel { string::ref })
            ),
            value1,
            null
        ) shouldBe true

        filterMatches(
            Or(
                Exists(TestMarykModel { reference::ref }),
                Exists(TestMarykModel { string::ref })
            ),
            value1,
            null
        ) shouldBe true

        filterMatches(
            Or(
                Exists(TestMarykModel { reference::ref }),
                Not(Exists(TestMarykModel { string::ref }))
            ),
            value1,
            null
        ) shouldBe false
    }
}
