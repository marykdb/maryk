package maryk.datastore.memory.processors

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import maryk.core.clock.HLC
import maryk.core.processors.datastore.writeToStorage
import maryk.core.models.IsRootDataModel
import maryk.core.models.key
import maryk.core.properties.types.Key
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
import maryk.test.models.TestMarykModel
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FilterWithFetchRequestKtTest {
    private val value2 = TestMarykModel.createDataRecord(
        TestMarykModel(
            string = "haha2",
            int = 532,
            uint = 2u,
            double = 2828.43,
            dateTime = LocalDateTime(2013, 3, 2, 0, 0),
            bool = true,
            map = mapOf(
                LocalTime(14, 15, 14) to "haha10"
            ),
            list = listOf(
                2, 6, 7
            ),
            set = setOf(
                LocalDate(2020, 3, 30), LocalDate(2018, 9, 9)
            )
        )
    )

    private val value1 = TestMarykModel.createDataRecord(
        TestMarykModel(
            string = "haha1",
            int = 5,
            uint = 6u,
            double = 0.43,
            dateTime = LocalDateTime(2018, 3, 2, 0, 0),
            bool = true,
            map = mapOf(
                LocalTime(12, 13, 14) to "haha10"
            ),
            list = listOf(
                4, 6, 7
            ),
            set = setOf(
                LocalDate(2019, 3, 30), LocalDate(2018, 9, 9)
            ),
            selfReference = value2.key
        )
    )

    private fun <DM : IsRootDataModel> DM.createDataRecord(values: Values<DM>): DataRecord<DM> {
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

    private val recordFetcher = { dataModel: IsRootDataModel, key: Key<*> ->
        when {
            dataModel === TestMarykModel -> when (key) {
                value1.key -> value1
                value2.key -> value2
                else -> null
            }
            else -> null
        }
    }

    @Test
    fun doExistsFilter() {
        assertTrue {
            filterMatches(
                Exists(TestMarykModel { string::ref }),
                value1,
                null,
                recordFetcher
            )
        }

        // Below version it did not exist
        assertFalse {
            filterMatches(
                Exists(TestMarykModel { string::ref }),
                value1,
                HLC(1233uL),
                recordFetcher
            )
        }

        assertFalse {
            filterMatches(
                Exists(TestMarykModel { reference::ref }),
                value1,
                null,
                recordFetcher
            )
        }
    }

    @Test
    fun doEqualsFilter() {
        assertTrue {
            filterMatches(
                Equals(TestMarykModel { string::ref } with "haha1"),
                value1,
                null,
                recordFetcher
            )
        }

        assertFalse {
            filterMatches(
                Equals(TestMarykModel { string::ref } with "haha1"),
                value1,
                HLC(1233uL),
                recordFetcher
            )
        }

        assertFalse {
            filterMatches(
                Equals(TestMarykModel { string::ref } with "wrong"),
                value1,
                null,
                recordFetcher
            )
        }
    }

    @Test
    fun doReferencedEqualsFilter() {
        assertTrue {
            filterMatches(
                Equals(TestMarykModel { selfReference { string::ref } } with "haha2"),
                value1,
                null,
                recordFetcher
            )
        }

        assertFalse {
            filterMatches(
                Equals(TestMarykModel { selfReference { string::ref } } with "haha2"),
                value1,
                HLC(1233uL),
                recordFetcher
            )
        }

        assertFalse {
            filterMatches(
                Equals(TestMarykModel { selfReference { string::ref } } with "wrong"),
                value1,
                null,
                recordFetcher
            )
        }
    }

    @Test
    fun doComplexMapListSetFilter() {
        assertTrue {
            filterMatches(
                Equals(TestMarykModel { map.refAt(LocalTime(12, 13, 14)) } with "haha10"),
                value1,
                null,
                recordFetcher
            )
        }

        assertTrue {
            filterMatches(
                Equals(TestMarykModel { map.refToAny() } with "haha10"),
                value1,
                null,
                recordFetcher
            )
        }

        assertFalse {
            filterMatches(
                Equals(TestMarykModel { map.refToAny() } with "haha11"),
                value1,
                null,
                recordFetcher
            )
        }

        assertFalse {
            filterMatches(
                Equals(TestMarykModel { map.refAt(LocalTime(13, 13, 14)) } with "haha10"),
                value1,
                null,
                recordFetcher
            )
        }

        assertTrue {
            filterMatches(
                Equals(TestMarykModel { list refAt 1u } with 6),
                value1,
                null,
                recordFetcher
            )
        }

        assertFalse {
            filterMatches(
                Equals(TestMarykModel { list refAt 2u } with 6),
                value1,
                null,
                recordFetcher
            )
        }

        assertTrue {
            filterMatches(
                Equals(TestMarykModel { list.refToAny() } with 6),
                value1,
                null,
                recordFetcher
            )
        }

        assertFalse {
            filterMatches(
                Equals(TestMarykModel { list.refToAny() } with 2),
                value1,
                null,
                recordFetcher
            )
        }

        assertTrue {
            filterMatches(
                Equals(TestMarykModel { list refAt 1u } with 6),
                value1,
                null,
                recordFetcher
            )
        }

        assertTrue {
            filterMatches(
                Exists(TestMarykModel { set refAt LocalDate(2018, 9, 9) }),
                value1,
                null,
                recordFetcher
            )
        }

        assertFalse {
            filterMatches(
                Exists(TestMarykModel { set refAt LocalDate(2017, 9, 9) }),
                value1,
                null,
                recordFetcher
            )
        }
    }

    @Test
    fun doPrefixFilter() {
        assertTrue {
            filterMatches(
                Prefix(TestMarykModel { string::ref } with "ha"),
                value1,
                null,
                recordFetcher
            )
        }

        assertFalse {
            filterMatches(
                Prefix(TestMarykModel { string::ref } with "wrong"),
                value1,
                null,
                recordFetcher
            )
        }
    }

    @Test
    fun doLessThanFilter() {
        assertTrue {
            filterMatches(
                LessThan(TestMarykModel { int::ref } with 6),
                value1,
                null,
                recordFetcher
            )
        }

        assertFalse {
            filterMatches(
                LessThan(TestMarykModel { int::ref } with 5),
                value1,
                null,
                recordFetcher
            )
        }

        assertFalse {
            filterMatches(
                LessThan(TestMarykModel { int::ref } with 2),
                value1,
                null,
                recordFetcher
            )
        }
    }

    @Test
    fun doLessThanEqualsFilter() {
        assertTrue {
            filterMatches(
                LessThanEquals(TestMarykModel { int::ref } with 6),
                value1,
                null,
                recordFetcher
            )
        }

        assertTrue {
            filterMatches(
                LessThanEquals(TestMarykModel { int::ref } with 5),
                value1,
                null,
                recordFetcher
            )
        }

        assertFalse {
            filterMatches(
                LessThanEquals(TestMarykModel { int::ref } with 2),
                value1,
                null,
                recordFetcher
            )
        }
    }

    @Test
    fun doGreaterThanFilter() {
        assertTrue {
            filterMatches(
                GreaterThan(TestMarykModel { int::ref } with 4),
                value1,
                null,
                recordFetcher
            )
        }

        assertFalse {
            filterMatches(
                GreaterThan(TestMarykModel { int::ref } with 5),
                value1,
                null,
                recordFetcher
            )
        }

        assertFalse {
            filterMatches(
                GreaterThan(TestMarykModel { int::ref } with 6),
                value1,
                null,
                recordFetcher
            )
        }
    }

    @Test
    fun doGreaterThanEqualsFilter() {
        assertTrue {
            filterMatches(
                GreaterThanEquals(TestMarykModel { int::ref } with 4),
                value1,
                null,
                recordFetcher
            )
        }

        assertTrue {
            filterMatches(
                GreaterThanEquals(TestMarykModel { int::ref } with 5),
                value1,
                null,
                recordFetcher
            )
        }

        assertFalse {
            filterMatches(
                GreaterThanEquals(TestMarykModel { int::ref } with 6),
                value1,
                null,
                recordFetcher
            )
        }
    }

    @Test
    fun doRangeFilter() {
        assertTrue {
            filterMatches(
                Range(TestMarykModel { int::ref } with (2..8)),
                value1,
                null,
                recordFetcher
            )
        }

        assertTrue {
            filterMatches(
                Range(TestMarykModel { int::ref } with (2..5)),
                value1,
                null,
                recordFetcher
            )
        }

        assertFalse {
            filterMatches(
                Range(TestMarykModel { int::ref } with (2..3)),
                value1,
                null,
                recordFetcher
            )
        }
    }

    @Test
    fun doRegExFilter() {
        assertTrue {
            filterMatches(
                RegEx(TestMarykModel { string::ref } with Regex("^h.*$")),
                value1,
                null,
                recordFetcher
            )
        }

        assertFalse {
            filterMatches(
                RegEx(TestMarykModel { string::ref } with Regex("^b.*$")),
                value1,
                null,
                recordFetcher
            )
        }
    }

    @Test
    fun doValueInFilter() {
        assertTrue {
            filterMatches(
                ValueIn(TestMarykModel { string::ref } with setOf("haha1", "haha2")),
                value1,
                null,
                recordFetcher
            )
        }

        assertFalse {
            filterMatches(
                ValueIn(TestMarykModel { string::ref } with setOf("no1", "no2")),
                value1,
                null,
                recordFetcher
            )
        }
    }

    @Test
    fun doNotFilter() {
        assertFalse {
            filterMatches(
                Not(Exists(TestMarykModel { string::ref })),
                value1,
                null,
                recordFetcher
            )
        }

        assertTrue {
            filterMatches(
                Not(Exists(TestMarykModel { reference::ref })),
                value1,
                null,
                recordFetcher
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
                null,
                recordFetcher
            )
        }

        assertFalse {
            filterMatches(
                And(
                    Exists(TestMarykModel { reference::ref }),
                    Exists(TestMarykModel { string::ref })
                ),
                value1,
                null,
                recordFetcher
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
                null,
                recordFetcher
            )
        }

        assertTrue {
            filterMatches(
                Or(
                    Exists(TestMarykModel { reference::ref }),
                    Exists(TestMarykModel { string::ref })
                ),
                value1,
                null,
                recordFetcher
            )
        }

        assertFalse {
            filterMatches(
                Or(
                    Exists(TestMarykModel { reference::ref }),
                    Not(Exists(TestMarykModel { string::ref }))
                ),
                value1,
                null,
                recordFetcher
            )
        }
    }
}
