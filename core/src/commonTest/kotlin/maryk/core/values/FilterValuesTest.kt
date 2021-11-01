package maryk.core.values

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
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
import maryk.lib.time.Time
import maryk.test.models.TestMarykModel
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FilterValuesTest {
    private val value2 = TestMarykModel(
        string = "haha2",
        int = 532,
        uint = 2u,
        double = 2828.43,
        dateTime = LocalDateTime(2013, 3, 2, 0, 0),
        bool = true,
        map = mapOf(
            Time(14, 15, 14) to "haha10"
        ),
        list = listOf(
            2, 6, 7
        ),
        set = setOf(
            LocalDate(2020, 3, 30), LocalDate(2018, 9, 9)
        )
    )

    private val value2Key = TestMarykModel.key(value2)

    private val value1 =
        TestMarykModel(
            string = "haha1",
            int = 5,
            uint = 6u,
            double = 0.43,
            dateTime = LocalDateTime(2018, 3, 2, 0, 0),
            bool = true,
            map = mapOf(
                Time(12, 13, 14) to "haha10"
            ),
            list = listOf(
                4, 6, 7
            ),
            set = setOf(
                LocalDate(2019, 3, 30), LocalDate(2018, 9, 9)
            ),
            selfReference = value2Key
        )

    @Test
    fun doExistsFilter() {
        assertTrue {
            value1.matches(
                Exists(TestMarykModel { string::ref })
            )
        }

        assertFalse {
            value1.matches(
                Exists(TestMarykModel { reference::ref })
            )
        }
    }

    @Test
    fun doEqualsFilter() {
        assertTrue {
            value1.matches(
                Equals(TestMarykModel { string::ref } with "haha1")
            )
        }

        assertFalse {
            value1.matches(
                Equals(TestMarykModel { string::ref } with "wrong")
            )
        }
    }

    @Test
    fun doComplexMapListSetFilter() {
        assertTrue {
            value1.matches(
                Equals(TestMarykModel { map.refAt(Time(12, 13, 14)) } with "haha10")
            )
        }

        assertTrue {
            value1.matches(
                Equals(TestMarykModel { map.refToAny() } with "haha10")
            )
        }

        assertFalse {
            value1.matches(
                Equals(TestMarykModel { map.refToAny() } with "haha11")
            )
        }

        assertFalse {
            value1.matches(
                Equals(TestMarykModel { map.refAt(Time(13, 13, 14)) } with "haha10")
            )
        }

        assertTrue {
            value1.matches(
                Equals(TestMarykModel { list refAt 1u } with 6)
            )
        }

        assertFalse {
            value1.matches(
                Equals(TestMarykModel { list refAt 2u } with 6)
            )
        }

        assertTrue {
            value1.matches(
                Equals(TestMarykModel { list.refToAny() } with 6)
            )
        }

        assertFalse {
            value1.matches(
                Equals(TestMarykModel { list.refToAny() } with 2)
            )
        }

        assertTrue {
            value1.matches(
                Equals(TestMarykModel { list refAt 1u } with 6)
            )
        }

        assertTrue {
            value1.matches(
                Exists(TestMarykModel { set refAt LocalDate(2018, 9, 9) })
            )
        }

        assertFalse {
            value1.matches(
                Exists(TestMarykModel { set refAt LocalDate(2017, 9, 9) })
            )
        }
    }

    @Test
    fun doPrefixFilter() {
        assertTrue {
            value1.matches(
                Prefix(TestMarykModel { string::ref } with "ha")
            )
        }

        assertFalse {
            value1.matches(
                Prefix(TestMarykModel { string::ref } with "wrong")
            )
        }
    }

    @Test
    fun doLessThanFilter() {
        assertTrue {
            value1.matches(
                LessThan(TestMarykModel { int::ref } with 6)
            )
        }

        assertFalse {
            value1.matches(
                LessThan(TestMarykModel { int::ref } with 5)
            )
        }

        assertFalse {
            value1.matches(
                LessThan(TestMarykModel { int::ref } with 2)
            )
        }
    }

    @Test
    fun doLessThanEqualsFilter() {
        assertTrue {
            value1.matches(
                LessThanEquals(TestMarykModel { int::ref } with 6)
            )
        }

        assertTrue {
            value1.matches(
                LessThanEquals(TestMarykModel { int::ref } with 5)
            )
        }

        assertFalse {
            value1.matches(
                LessThanEquals(TestMarykModel { int::ref } with 2)
            )
        }
    }

    @Test
    fun doGreaterThanFilter() {
        assertTrue {
            value1.matches(
                GreaterThan(TestMarykModel { int::ref } with 4)
            )
        }

        assertFalse {
            value1.matches(
                GreaterThan(TestMarykModel { int::ref } with 5)
            )
        }

        assertFalse {
            value1.matches(
                GreaterThan(TestMarykModel { int::ref } with 6)
            )
        }
    }

    @Test
    fun doGreaterThanEqualsFilter() {
        assertTrue {
            value1.matches(
                GreaterThanEquals(TestMarykModel { int::ref } with 4)
            )
        }

        assertTrue {
            value1.matches(
                GreaterThanEquals(TestMarykModel { int::ref } with 5)
            )
        }

        assertFalse {
            value1.matches(
                GreaterThanEquals(TestMarykModel { int::ref } with 6)
            )
        }
    }

    @Test
    fun doRangeFilter() {
        assertTrue {
            value1.matches(
                Range(TestMarykModel { int::ref } with (2..8))
            )
        }

        assertTrue {
            value1.matches(
                Range(TestMarykModel { int::ref } with (2..5))
            )
        }

        assertFalse {
            value1.matches(
                Range(TestMarykModel { int::ref } with (2..3))
            )
        }
    }

    @Test
    fun doRegExFilter() {
        assertTrue {
            value1.matches(
                RegEx(TestMarykModel { string::ref } with Regex("^h.*$"))
            )
        }

        assertFalse {
            value1.matches(
                RegEx(TestMarykModel { string::ref } with Regex("^b.*$"))
            )
        }
    }

    @Test
    fun doValueInFilter() {
        assertTrue {
            value1.matches(
                ValueIn(TestMarykModel { string::ref } with setOf("haha1", "haha2"))
            )
        }

        assertFalse {
            value1.matches(
                ValueIn(TestMarykModel { string::ref } with setOf("no1", "no2"))
            )
        }
    }

    @Test
    fun doNotFilter() {
        assertFalse {
            value1.matches(
                Not(Exists(TestMarykModel { string::ref }))
            )
        }

        assertTrue {
            value1.matches(
                Not(Exists(TestMarykModel { reference::ref }))
            )
        }
    }

    @Test
    fun doAndFilter() {
        assertTrue {
            value1.matches(
                And(
                    Exists(TestMarykModel { int::ref }),
                    Exists(TestMarykModel { string::ref })
                )
            )
        }

        assertFalse {
            value1.matches(
                And(
                    Exists(TestMarykModel { reference::ref }),
                    Exists(TestMarykModel { string::ref })
                )
            )
        }
    }

    @Test
    fun doOrFilter() {
        assertTrue {
            value1.matches(
                Or(
                    Exists(TestMarykModel { int::ref }),
                    Exists(TestMarykModel { string::ref })
                )
            )
        }

        assertTrue {
            value1.matches(
                Or(
                    Exists(TestMarykModel { reference::ref }),
                    Exists(TestMarykModel { string::ref })
                )
            )
        }

        assertFalse {
            value1.matches(
                Or(
                    Exists(TestMarykModel { reference::ref }),
                    Not(Exists(TestMarykModel { string::ref }))
                )
            )
        }
    }
}
