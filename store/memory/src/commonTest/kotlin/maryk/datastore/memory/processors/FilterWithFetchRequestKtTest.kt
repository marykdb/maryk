@file:Suppress("EXPERIMENTAL_API_USAGE", "EXPERIMENTAL_UNSIGNED_LITERALS")

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
import maryk.lib.time.DateTime
import maryk.test.models.TestMarykModel
import maryk.test.shouldBe
import kotlin.test.Test

class FilterWithFetchRequestKtTest {
    private val value1 = TestMarykModel.createDataRecord(
        TestMarykModel("haha1", 5, 6u, 0.43, DateTime(2018, 3, 2), true)
    )

    private fun <DM: IsRootValuesDataModel<P>, P: PropertyDefinitions> DM.createDataRecord(values: Values<DM, P>): DataRecord<DM, P> {
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
        doFilter(
            Exists(TestMarykModel.ref { string }),
            value1,
            null
        ) shouldBe true

        // Below version it did not exist
        doFilter(
            Exists(TestMarykModel.ref { string }),
            value1,
            1233uL
        ) shouldBe false

        doFilter(
            Exists(TestMarykModel.ref { reference }),
            value1,
            null
        ) shouldBe false
    }

    @Test
    fun doEqualsFilter() {
        doFilter(
            Equals(TestMarykModel.ref { string } with "haha1"),
            value1,
            null
        ) shouldBe true

        doFilter(
            Equals(TestMarykModel.ref { string } with "haha1"),
            value1,
            1233uL
        ) shouldBe false

        doFilter(
            Equals(TestMarykModel.ref { string } with "wrong"),
            value1,
            null
        ) shouldBe false
    }

    @Test
    fun doPrefixFilter() {
        doFilter(
            Prefix(TestMarykModel.ref { string } with "ha"),
            value1,
            null
        ) shouldBe true

        doFilter(
            Prefix(TestMarykModel.ref { string } with "wrong"),
            value1,
            null
        ) shouldBe false
    }

    @Test
    fun doLessThanFilter() {
        doFilter(
            LessThan(TestMarykModel.ref { int } with 6),
            value1,
            null
        ) shouldBe true

        doFilter(
            LessThan(TestMarykModel.ref { int } with 5),
            value1,
            null
        ) shouldBe false

        doFilter(
            LessThan(TestMarykModel.ref { int } with 2),
            value1,
            null
        ) shouldBe false
    }

    @Test
    fun doLessThanEqualsFilter() {
        doFilter(
            LessThanEquals(TestMarykModel.ref { int } with 6),
            value1,
            null
        ) shouldBe true

        doFilter(
            LessThanEquals(TestMarykModel.ref { int } with 5),
            value1,
            null
        ) shouldBe true

        doFilter(
            LessThanEquals(TestMarykModel.ref { int } with 2),
            value1,
            null
        ) shouldBe false
    }

    @Test
    fun doGreaterThanFilter() {
        doFilter(
            GreaterThan(TestMarykModel.ref { int } with 4),
            value1,
            null
        ) shouldBe true

        doFilter(
            GreaterThan(TestMarykModel.ref { int } with 5),
            value1,
            null
        ) shouldBe false

        doFilter(
            GreaterThan(TestMarykModel.ref { int } with 6),
            value1,
            null
        ) shouldBe false
    }

    @Test
    fun doGreaterThanEqualsFilter() {
        doFilter(
            GreaterThanEquals(TestMarykModel.ref { int } with 4),
            value1,
            null
        ) shouldBe true

        doFilter(
            GreaterThanEquals(TestMarykModel.ref { int } with 5),
            value1,
            null
        ) shouldBe true

        doFilter(
            GreaterThanEquals(TestMarykModel.ref { int } with 6),
            value1,
            null
        ) shouldBe false
    }

    @Test
    fun doRangeFilter() {
        doFilter(
            Range(TestMarykModel.ref { int } with (2..8)),
            value1,
            null
        ) shouldBe true

        doFilter(
            Range(TestMarykModel.ref { int } with (2..5)),
            value1,
            null
        ) shouldBe true

        doFilter(
            Range(TestMarykModel.ref { int } with (2..3)),
            value1,
            null
        ) shouldBe false
    }

    @Test
    fun doRegExFilter() {
        doFilter(
            RegEx(TestMarykModel.ref { string } with Regex("^h.*$")),
            value1,
            null
        ) shouldBe true

        doFilter(
            RegEx(TestMarykModel.ref { string } with Regex("^b.*$")),
            value1,
            null
        ) shouldBe false
    }

    @Test
    fun doValueInFilter() {
        doFilter(
            ValueIn(TestMarykModel.ref { string } with setOf("haha1", "haha2")),
            value1,
            null
        ) shouldBe true

        doFilter(
            ValueIn(TestMarykModel.ref { string } with setOf("no1", "no2")),
            value1,
            null
        ) shouldBe false
    }

    @Test
    fun doNotFilter() {
        doFilter(
            Not(Exists(TestMarykModel.ref { string })),
            value1,
            null
        ) shouldBe false

        doFilter(
            Not(Exists(TestMarykModel.ref { reference })),
            value1,
            null
        ) shouldBe true
    }

    @Test
    fun doAndFilter() {
        doFilter(
            And(
                Exists(TestMarykModel.ref { int }),
                Exists(TestMarykModel.ref { string })
            ),
            value1,
            null
        ) shouldBe true

        doFilter(
            And(
                Exists(TestMarykModel.ref { reference }),
                Exists(TestMarykModel.ref { string })
            ),
            value1,
            null
        ) shouldBe false
    }

    @Test
    fun doOrFilter() {
        doFilter(
            Or(
                Exists(TestMarykModel.ref { int }),
                Exists(TestMarykModel.ref { string })
            ),
            value1,
            null
        ) shouldBe true

        doFilter(
            Or(
                Exists(TestMarykModel.ref { reference }),
                Exists(TestMarykModel.ref { string })
            ),
            value1,
            null
        ) shouldBe true

        doFilter(
            Or(
                Exists(TestMarykModel.ref { reference }),
                Not(Exists(TestMarykModel.ref { string }))
            ),
            value1,
            null
        ) shouldBe false
    }
}
