package maryk.datastore.memory.processors

import maryk.core.clock.HLC
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
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

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
            recordValues += DataRecordValue(reference, value, HLC(1234uL))
        }

        return DataRecord(
            key = this.key(values),
            firstVersion = HLC(1234uL),
            lastVersion = HLC(1234uL),
            values = recordValues
        )
    }

    @Test
    fun doExistsFilter() {
        assertTrue {
            filterMatches(
                Exists(TestMarykModel { string::ref }),
                value1,
                null
            )
        }

        // Below version it did not exist
        assertFalse {
            filterMatches(
                Exists(TestMarykModel { string::ref }),
                value1,
                HLC(1233uL)
            )
        }

        assertFalse {
            filterMatches(
                Exists(TestMarykModel { reference::ref }),
                value1,
                null
            )
        }
    }

    @Test
    fun doEqualsFilter() {
        assertTrue {
            filterMatches(
                Equals(TestMarykModel { string::ref } with "haha1"),
                value1,
                null
            )
        }

        assertFalse {
            filterMatches(
                Equals(TestMarykModel { string::ref } with "haha1"),
                value1,
                HLC(1233uL)
            )
        }

        assertFalse {
            filterMatches(
                Equals(TestMarykModel { string::ref } with "wrong"),
                value1,
                null
            )
        }
    }

    @Test
    fun doComplexMapListSetFilter() {
        assertTrue {
            filterMatches(
                Equals(TestMarykModel { map.refAt(Time(12, 13, 14)) } with "haha10"),
                value1,
                null
            )
        }

        assertTrue {
            filterMatches(
                Equals(TestMarykModel { map.refToAny() } with "haha10"),
                value1,
                null
            )
        }

        assertFalse {
            filterMatches(
                Equals(TestMarykModel { map.refToAny() } with "haha11"),
                value1,
                null
            )
        }

        assertFalse {
            filterMatches(
                Equals(TestMarykModel { map.refAt(Time(13, 13, 14)) } with "haha10"),
                value1,
                null
            )
        }

        assertTrue {
            filterMatches(
                Equals(TestMarykModel { list refAt 1u } with 6),
                value1,
                null
            )
        }

        assertFalse {
            filterMatches(
                Equals(TestMarykModel { list refAt 2u } with 6),
                value1,
                null
            )
        }

        assertTrue {
            filterMatches(
                Equals(TestMarykModel { list.refToAny() } with 6),
                value1,
                null
            )
        }

        assertFalse {
            filterMatches(
                Equals(TestMarykModel { list.refToAny() } with 2),
                value1,
                null
            )
        }

        assertTrue {
            filterMatches(
                Equals(TestMarykModel { list refAt 1u } with 6),
                value1,
                null
            )
        }

        assertTrue {
            filterMatches(
                Exists(TestMarykModel { set refAt Date(2018, 9, 9) }),
                value1,
                null
            )
        }

        assertFalse {
            filterMatches(
                Exists(TestMarykModel { set refAt Date(2017, 9, 9) }),
                value1,
                null
            )
        }
    }

    @Test
    fun doPrefixFilter() {
        assertTrue {
            filterMatches(
                Prefix(TestMarykModel { string::ref } with "ha"),
                value1,
                null
            )
        }

        assertFalse {
            filterMatches(
                Prefix(TestMarykModel { string::ref } with "wrong"),
                value1,
                null
            )
        }
    }

    @Test
    fun doLessThanFilter() {
        assertTrue {
            filterMatches(
                LessThan(TestMarykModel { int::ref } with 6),
                value1,
                null
            )
        }

        assertFalse {
            filterMatches(
                LessThan(TestMarykModel { int::ref } with 5),
                value1,
                null
            )
        }

        assertFalse {
            filterMatches(
                LessThan(TestMarykModel { int::ref } with 2),
                value1,
                null
            )
        }
    }

    @Test
    fun doLessThanEqualsFilter() {
        assertTrue {
            filterMatches(
                LessThanEquals(TestMarykModel { int::ref } with 6),
                value1,
                null
            )
        }

        assertTrue {
            filterMatches(
                LessThanEquals(TestMarykModel { int::ref } with 5),
                value1,
                null
            )
        }

        assertFalse {
            filterMatches(
                LessThanEquals(TestMarykModel { int::ref } with 2),
                value1,
                null
            )
        }
    }

    @Test
    fun doGreaterThanFilter() {
        assertTrue {
            filterMatches(
                GreaterThan(TestMarykModel { int::ref } with 4),
                value1,
                null
            )
        }

        assertFalse {
            filterMatches(
                GreaterThan(TestMarykModel { int::ref } with 5),
                value1,
                null
            )
        }

        assertFalse {
            filterMatches(
                GreaterThan(TestMarykModel { int::ref } with 6),
                value1,
                null
            )
        }
    }

    @Test
    fun doGreaterThanEqualsFilter() {
        assertTrue {
            filterMatches(
                GreaterThanEquals(TestMarykModel { int::ref } with 4),
                value1,
                null
            )
        }

        assertTrue {
            filterMatches(
                GreaterThanEquals(TestMarykModel { int::ref } with 5),
                value1,
                null
            )
        }

        assertFalse {
            filterMatches(
                GreaterThanEquals(TestMarykModel { int::ref } with 6),
                value1,
                null
            )
        }
    }

    @Test
    fun doRangeFilter() {
        assertTrue {
            filterMatches(
                Range(TestMarykModel { int::ref } with (2..8)),
                value1,
                null
            )
        }

        assertTrue {
            filterMatches(
                Range(TestMarykModel { int::ref } with (2..5)),
                value1,
                null
            )
        }

        assertFalse {
            filterMatches(
                Range(TestMarykModel { int::ref } with (2..3)),
                value1,
                null
            )
        }
    }

    @Test
    fun doRegExFilter() {
        assertTrue {
            filterMatches(
                RegEx(TestMarykModel { string::ref } with Regex("^h.*$")),
                value1,
                null
            )
        }

        assertFalse {
            filterMatches(
                RegEx(TestMarykModel { string::ref } with Regex("^b.*$")),
                value1,
                null
            )
        }
    }

    @Test
    fun doValueInFilter() {
        assertTrue {
            filterMatches(
                ValueIn(TestMarykModel { string::ref } with setOf("haha1", "haha2")),
                value1,
                null
            )
        }

        assertFalse {
            filterMatches(
                ValueIn(TestMarykModel { string::ref } with setOf("no1", "no2")),
                value1,
                null
            )
        }
    }

    @Test
    fun doNotFilter() {
        assertFalse {
            filterMatches(
                Not(Exists(TestMarykModel { string::ref })),
                value1,
                null
            )
        }

        assertTrue {
            filterMatches(
                Not(Exists(TestMarykModel { reference::ref })),
                value1,
                null
            )
        }
    }

    @Test
    fun doAndFilter() {
        assertTrue {
            filterMatches(
                And(
                    Exists(TestMarykModel { int::ref }),
                    Exists(TestMarykModel { string::ref })
                ),
                value1,
                null
            )
        }

        assertFalse {
            filterMatches(
                And(
                    Exists(TestMarykModel { reference::ref }),
                    Exists(TestMarykModel { string::ref })
                ),
                value1,
                null
            )
        }
    }

    @Test
    fun doOrFilter() {
        assertTrue {
            filterMatches(
                Or(
                    Exists(TestMarykModel { int::ref }),
                    Exists(TestMarykModel { string::ref })
                ),
                value1,
                null
            )
        }

        assertTrue {
            filterMatches(
                Or(
                    Exists(TestMarykModel { reference::ref }),
                    Exists(TestMarykModel { string::ref })
                ),
                value1,
                null
            )
        }

        assertFalse {
            filterMatches(
                Or(
                    Exists(TestMarykModel { reference::ref }),
                    Not(Exists(TestMarykModel { string::ref }))
                ),
                value1,
                null
            )
        }
    }
}
