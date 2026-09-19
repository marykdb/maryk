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
        TestMarykModel.create {
            string with "haha2"
            int with 532
            uint with 2u
            double with 2828.43
            dateTime with LocalDateTime(2013, 3, 2, 0, 0)
            bool with true
            map with mapOf(
                LocalTime(14, 15, 14) to "haha10"
            )
            list with listOf(
                2, 6, 7
            )
            set with setOf(
                LocalDate(2020, 3, 30), LocalDate(2018, 9, 9)
            )
        }
    )

    private val value1 = TestMarykModel.createDataRecord(
        TestMarykModel.create {
            string with "haha1"
            int with 5
            uint with 6u
            double with 0.43
            dateTime with LocalDateTime(2018, 3, 2, 0, 0)
            bool with true
            map with mapOf(
                LocalTime(12, 13, 14) to "haha10"
            )
            list with listOf(
                4, 6, 7
            )
            set with setOf(
                LocalDate(2019, 3, 30), LocalDate(2018, 9, 9)
            )
            selfReference with value2.key
        }
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
                Exists(TestMarykModel.ref { string }),
                value1,
                null,
                recordFetcher
            )
        }

        // Below version it did not exist
        assertFalse {
            filterMatches(
                Exists(TestMarykModel.ref { string }),
                value1,
                HLC(1233uL),
                recordFetcher
            )
        }

        assertFalse {
            filterMatches(
                Exists(TestMarykModel.ref { reference }),
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
                Equals(TestMarykModel.ref { string } with "haha1"),
                value1,
                null,
                recordFetcher
            )
        }

        assertFalse {
            filterMatches(
                Equals(TestMarykModel.ref { string } with "haha1"),
                value1,
                HLC(1233uL),
                recordFetcher
            )
        }

        assertFalse {
            filterMatches(
                Equals(TestMarykModel.ref { string } with "wrong"),
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
                Equals(TestMarykModel.ref { selfReference { string } } with "haha2"),
                value1,
                null,
                recordFetcher
            )
        }

        assertFalse {
            filterMatches(
                Equals(TestMarykModel.ref { selfReference { string } } with "haha2"),
                value1,
                HLC(1233uL),
                recordFetcher
            )
        }

        assertFalse {
            filterMatches(
                Equals(TestMarykModel.ref { selfReference { string } } with "wrong"),
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
                Equals(TestMarykModel.ref { map.at(LocalTime(12, 13, 14)) } with "haha10"),
                value1,
                null,
                recordFetcher
            )
        }

        assertTrue {
            filterMatches(
                Equals(TestMarykModel.ref { map.anyValue() } with "haha10"),
                value1,
                null,
                recordFetcher
            )
        }

        assertFalse {
            filterMatches(
                Equals(TestMarykModel.ref { map.anyValue() } with "haha11"),
                value1,
                null,
                recordFetcher
            )
        }

        assertFalse {
            filterMatches(
                Equals(TestMarykModel.ref { map.at(LocalTime(13, 13, 14)) } with "haha10"),
                value1,
                null,
                recordFetcher
            )
        }

        assertTrue {
            filterMatches(
                Equals(TestMarykModel.ref { list at 1u } with 6),
                value1,
                null,
                recordFetcher
            )
        }

        assertFalse {
            filterMatches(
                Equals(TestMarykModel.ref { list at 2u } with 6),
                value1,
                null,
                recordFetcher
            )
        }

        assertTrue {
            filterMatches(
                Equals(TestMarykModel.ref { list.any() } with 6),
                value1,
                null,
                recordFetcher
            )
        }

        assertFalse {
            filterMatches(
                Equals(TestMarykModel.ref { list.any() } with 2),
                value1,
                null,
                recordFetcher
            )
        }

        assertTrue {
            filterMatches(
                Equals(TestMarykModel.ref { list at 1u } with 6),
                value1,
                null,
                recordFetcher
            )
        }

        assertTrue {
            filterMatches(
                Exists(TestMarykModel.ref { set item LocalDate(2018, 9, 9) }),
                value1,
                null,
                recordFetcher
            )
        }

        assertFalse {
            filterMatches(
                Exists(TestMarykModel.ref { set item LocalDate(2017, 9, 9) }),
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
                Prefix(TestMarykModel.ref { string } with "ha"),
                value1,
                null,
                recordFetcher
            )
        }

        assertFalse {
            filterMatches(
                Prefix(TestMarykModel.ref { string } with "wrong"),
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
                LessThan(TestMarykModel.ref { int } with 6),
                value1,
                null,
                recordFetcher
            )
        }

        assertFalse {
            filterMatches(
                LessThan(TestMarykModel.ref { int } with 5),
                value1,
                null,
                recordFetcher
            )
        }

        assertFalse {
            filterMatches(
                LessThan(TestMarykModel.ref { int } with 2),
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
                LessThanEquals(TestMarykModel.ref { int } with 6),
                value1,
                null,
                recordFetcher
            )
        }

        assertTrue {
            filterMatches(
                LessThanEquals(TestMarykModel.ref { int } with 5),
                value1,
                null,
                recordFetcher
            )
        }

        assertFalse {
            filterMatches(
                LessThanEquals(TestMarykModel.ref { int } with 2),
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
                GreaterThan(TestMarykModel.ref { int } with 4),
                value1,
                null,
                recordFetcher
            )
        }

        assertFalse {
            filterMatches(
                GreaterThan(TestMarykModel.ref { int } with 5),
                value1,
                null,
                recordFetcher
            )
        }

        assertFalse {
            filterMatches(
                GreaterThan(TestMarykModel.ref { int } with 6),
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
                GreaterThanEquals(TestMarykModel.ref { int } with 4),
                value1,
                null,
                recordFetcher
            )
        }

        assertTrue {
            filterMatches(
                GreaterThanEquals(TestMarykModel.ref { int } with 5),
                value1,
                null,
                recordFetcher
            )
        }

        assertFalse {
            filterMatches(
                GreaterThanEquals(TestMarykModel.ref { int } with 6),
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
                Range(TestMarykModel.ref { int } with (2..8)),
                value1,
                null,
                recordFetcher
            )
        }

        assertTrue {
            filterMatches(
                Range(TestMarykModel.ref { int } with (2..5)),
                value1,
                null,
                recordFetcher
            )
        }

        assertFalse {
            filterMatches(
                Range(TestMarykModel.ref { int } with (2..3)),
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
                RegEx(TestMarykModel.ref { string } with Regex("^h.*$")),
                value1,
                null,
                recordFetcher
            )
        }

        assertFalse {
            filterMatches(
                RegEx(TestMarykModel.ref { string } with Regex("^b.*$")),
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
                ValueIn(TestMarykModel.ref { string } with setOf("haha1", "haha2")),
                value1,
                null,
                recordFetcher
            )
        }

        assertFalse {
            filterMatches(
                ValueIn(TestMarykModel.ref { string } with setOf("no1", "no2")),
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
                Not(Exists(TestMarykModel.ref { string })),
                value1,
                null,
                recordFetcher
            )
        }

        assertTrue {
            filterMatches(
                Not(Exists(TestMarykModel.ref { reference })),
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
                    Exists(TestMarykModel.ref { int }),
                    Exists(TestMarykModel.ref { string })
                ),
                value1,
                null,
                recordFetcher
            )
        }

        assertFalse {
            filterMatches(
                And(
                    Exists(TestMarykModel.ref { reference }),
                    Exists(TestMarykModel.ref { string })
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
                    Exists(TestMarykModel.ref { int }),
                    Exists(TestMarykModel.ref { string })
                ),
                value1,
                null,
                recordFetcher
            )
        }

        assertTrue {
            filterMatches(
                Or(
                    Exists(TestMarykModel.ref { reference }),
                    Exists(TestMarykModel.ref { string })
                ),
                value1,
                null,
                recordFetcher
            )
        }

        assertFalse {
            filterMatches(
                Or(
                    Exists(TestMarykModel.ref { reference }),
                    Not(Exists(TestMarykModel.ref { string }))
                ),
                value1,
                null,
                recordFetcher
            )
        }
    }
}
