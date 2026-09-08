package maryk.core.values

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import maryk.core.models.key
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
import maryk.test.models.TestMarykModel
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FilterValuesTest {
    private val value2 = TestMarykModel.create {
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

    private val value2Key = TestMarykModel.key(value2)

    private val value1 =
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
            selfReference with value2Key
        }

    @Test
    fun doExistsFilter() {
        assertTrue {
            value1.matches(
                Exists(TestMarykModel.ref { string })
            )
        }

        assertFalse {
            value1.matches(
                Exists(TestMarykModel.ref { reference })
            )
        }
    }

    @Test
    fun doEqualsFilter() {
        assertTrue {
            value1.matches(
                Equals(TestMarykModel.ref { string } with "haha1")
            )
        }

        assertFalse {
            value1.matches(
                Equals(TestMarykModel.ref { string } with "wrong")
            )
        }
    }

    @Test
    fun doComplexMapListSetFilter() {
        assertTrue {
            value1.matches(
                Equals(TestMarykModel.ref { map.at(LocalTime(12, 13, 14)) } with "haha10")
            )
        }

        assertTrue {
            value1.matches(
                Equals(TestMarykModel.ref { map.anyValue() } with "haha10")
            )
        }

        assertFalse {
            value1.matches(
                Equals(TestMarykModel.ref { map.anyValue() } with "haha11")
            )
        }

        assertFalse {
            value1.matches(
                Equals(TestMarykModel.ref { map.at(LocalTime(13, 13, 14)) } with "haha10")
            )
        }

        assertTrue {
            value1.matches(
                Equals(TestMarykModel.ref { list at 1u } with 6)
            )
        }

        assertFalse {
            value1.matches(
                Equals(TestMarykModel.ref { list at 2u } with 6)
            )
        }

        assertTrue {
            value1.matches(
                Equals(TestMarykModel.ref { list.any() } with 6)
            )
        }

        assertFalse {
            value1.matches(
                Equals(TestMarykModel.ref { list.any() } with 2)
            )
        }

        assertTrue {
            value1.matches(
                Equals(TestMarykModel.ref { list at 1u } with 6)
            )
        }

        assertTrue {
            value1.matches(
                Exists(TestMarykModel.ref { set item LocalDate(2018, 9, 9) })
            )
        }

        assertFalse {
            value1.matches(
                Exists(TestMarykModel.ref { set item LocalDate(2017, 9, 9) })
            )
        }
    }

    @Test
    fun doPrefixFilter() {
        assertTrue {
            value1.matches(
                Prefix(TestMarykModel.ref { string } with "ha")
            )
        }

        assertFalse {
            value1.matches(
                Prefix(TestMarykModel.ref { string } with "wrong")
            )
        }
    }

    @Test
    fun doLessThanFilter() {
        assertTrue {
            value1.matches(
                LessThan(TestMarykModel.ref { int } with 6)
            )
        }

        assertFalse {
            value1.matches(
                LessThan(TestMarykModel.ref { int } with 5)
            )
        }

        assertFalse {
            value1.matches(
                LessThan(TestMarykModel.ref { int } with 2)
            )
        }
    }

    @Test
    fun doLessThanEqualsFilter() {
        assertTrue {
            value1.matches(
                LessThanEquals(TestMarykModel.ref { int } with 6)
            )
        }

        assertTrue {
            value1.matches(
                LessThanEquals(TestMarykModel.ref { int } with 5)
            )
        }

        assertFalse {
            value1.matches(
                LessThanEquals(TestMarykModel.ref { int } with 2)
            )
        }
    }

    @Test
    fun doGreaterThanFilter() {
        assertTrue {
            value1.matches(
                GreaterThan(TestMarykModel.ref { int } with 4)
            )
        }

        assertFalse {
            value1.matches(
                GreaterThan(TestMarykModel.ref { int } with 5)
            )
        }

        assertFalse {
            value1.matches(
                GreaterThan(TestMarykModel.ref { int } with 6)
            )
        }
    }

    @Test
    fun doGreaterThanEqualsFilter() {
        assertTrue {
            value1.matches(
                GreaterThanEquals(TestMarykModel.ref { int } with 4)
            )
        }

        assertTrue {
            value1.matches(
                GreaterThanEquals(TestMarykModel.ref { int } with 5)
            )
        }

        assertFalse {
            value1.matches(
                GreaterThanEquals(TestMarykModel.ref { int } with 6)
            )
        }
    }

    @Test
    fun doRangeFilter() {
        assertTrue {
            value1.matches(
                Range(TestMarykModel.ref { int } with (2..8))
            )
        }

        assertTrue {
            value1.matches(
                Range(TestMarykModel.ref { int } with (2..5))
            )
        }

        assertFalse {
            value1.matches(
                Range(TestMarykModel.ref { int } with (2..3))
            )
        }
    }

    @Test
    fun doRegExFilter() {
        assertTrue {
            value1.matches(
                RegEx(TestMarykModel.ref { string } with Regex("^h.*$"))
            )
        }

        assertFalse {
            value1.matches(
                RegEx(TestMarykModel.ref { string } with Regex("^b.*$"))
            )
        }
    }

    @Test
    fun doValueInFilter() {
        assertTrue {
            value1.matches(
                ValueIn(TestMarykModel.ref { string } with setOf("haha1", "haha2"))
            )
        }

        assertFalse {
            value1.matches(
                ValueIn(TestMarykModel.ref { string } with setOf("no1", "no2"))
            )
        }
    }

    @Test
    fun doNotFilter() {
        assertFalse {
            value1.matches(
                Not(Exists(TestMarykModel.ref { string }))
            )
        }

        assertTrue {
            value1.matches(
                Not(Exists(TestMarykModel.ref { reference }))
            )
        }
    }

    @Test
    fun doAndFilter() {
        assertTrue {
            value1.matches(
                And(
                    Exists(TestMarykModel.ref { int }),
                    Exists(TestMarykModel.ref { string })
                )
            )
        }

        assertFalse {
            value1.matches(
                And(
                    Exists(TestMarykModel.ref { reference }),
                    Exists(TestMarykModel.ref { string })
                )
            )
        }
    }

    @Test
    fun doOrFilter() {
        assertTrue {
            value1.matches(
                Or(
                    Exists(TestMarykModel.ref { int }),
                    Exists(TestMarykModel.ref { string })
                )
            )
        }

        assertTrue {
            value1.matches(
                Or(
                    Exists(TestMarykModel.ref { reference }),
                    Exists(TestMarykModel.ref { string })
                )
            )
        }

        assertFalse {
            value1.matches(
                Or(
                    Exists(TestMarykModel.ref { reference }),
                    Not(Exists(TestMarykModel.ref { string }))
                )
            )
        }
    }
}
