package maryk.datastore.test

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import maryk.core.clock.HLC
import maryk.core.properties.types.Key
import maryk.core.query.changes.Change
import maryk.core.query.changes.change
import maryk.core.query.filters.And
import maryk.core.query.filters.Equals
import maryk.core.query.filters.Exists
import maryk.core.query.filters.GreaterThan
import maryk.core.query.filters.GreaterThanEquals
import maryk.core.query.filters.IsFilter
import maryk.core.query.filters.LessThan
import maryk.core.query.filters.LessThanEquals
import maryk.core.query.filters.Not
import maryk.core.query.filters.Or
import maryk.core.query.filters.Prefix
import maryk.core.query.filters.Range
import maryk.core.query.filters.RegEx
import maryk.core.query.filters.ValueIn
import maryk.core.query.pairs.with
import maryk.core.query.requests.add
import maryk.core.query.requests.change
import maryk.core.query.requests.delete
import maryk.core.query.requests.get
import maryk.core.query.responses.statuses.AddSuccess
import maryk.core.query.responses.statuses.ChangeSuccess
import maryk.datastore.shared.IsDataStore
import maryk.lib.time.Time
import maryk.test.assertType
import maryk.test.models.TestMarykModel
import maryk.test.runSuspendingTest
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DataStoreFilterTest(
    val dataStore: IsDataStore
) : IsDataStoreTest {

    private val keys = mutableListOf<Key<TestMarykModel>>()
    private val lastVersions = mutableListOf<ULong>()
    private lateinit var firstKey: Key<TestMarykModel>

    override val allTests = mapOf(
        "doExistsFilter" to ::doExistsFilter,
        "doEqualsFilter" to ::doEqualsFilter,
        "doComplexMapListSetFilter" to ::doComplexMapListSetFilter,
        "doPrefixFilter" to ::doPrefixFilter,
        "doLessThanFilter" to ::doLessThanFilter,
        "doLessThanEqualsFilter" to ::doLessThanEqualsFilter,
        "doGreaterThanFilter" to ::doGreaterThanFilter,
        "doGreaterThanEqualsFilter" to ::doGreaterThanEqualsFilter,
        "doRangeFilter" to ::doRangeFilter,
        "doRegExFilter" to ::doRegExFilter,
        "doValueInFilter" to ::doValueInFilter,
        "doNotFilter" to ::doNotFilter,
        "doAndFilter" to ::doAndFilter,
        "doOrFilter" to ::doOrFilter,
        "doReferencedEqualsFilter" to ::doReferencedEqualsFilter
    )

    private val dataObject = TestMarykModel(
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
        )
    )

    private val dataObject2 = TestMarykModel(
        string = "haha2",
        int = 3,
        uint = 5u,
        double = 0.23,
        dateTime = LocalDateTime(2016, 2, 20, 0, 0),
        bool = false,
        map = mapOf(
            Time(17, 16, 15) to "haha1"
        ),
        list = listOf(
            3, 4, 6
        ),
        set = setOf(
            LocalDate(2020, 2, 20), LocalDate(2013, 4, 19)
        )
    )

    override fun initData() {
        runSuspendingTest {
            val addResponse = dataStore.execute(
                TestMarykModel.add(
                    dataObject,
                    dataObject2
                )
            )

            addResponse.statuses.forEach { status ->
                val response = assertType<AddSuccess<TestMarykModel>>(status)
                keys.add(response.key)
                lastVersions.add(response.version)
            }

            val changeResponse = dataStore.execute(
                TestMarykModel.change(
                    keys[0].change(
                        Change(TestMarykModel { reference::ref } with keys[1])
                    )
                )
            )

            changeResponse.statuses.forEach { status ->
                val response = assertType<ChangeSuccess<TestMarykModel>>(status)
                lastVersions.add(response.version)
            }

            firstKey = keys[0]
        }
    }

    override fun resetData() {
        runSuspendingTest {
            dataStore.execute(
                TestMarykModel.delete(*keys.toTypedArray(), hardDelete = true)
            )
        }
        keys.clear()
        lastVersions.clear()
    }

    private fun filterMatches(filter: IsFilter, hlc: HLC? = null) =
        runSuspendingTest {
            val response = dataStore.execute(
                TestMarykModel.get(
                    firstKey,
                    where = filter,
                    toVersion = hlc?.timestamp
                )
            )
            response.values.isNotEmpty()
        }

    private fun doExistsFilter() = runSuspendingTest {
        assertTrue {
            filterMatches(
                Exists(TestMarykModel { string::ref })
            )
        }

        if (dataStore.keepAllVersions) {
            // Below version it did not exist
            assertFalse {
                filterMatches(
                    Exists(TestMarykModel { string::ref }),
                    HLC(lastVersions.first() - 1u)
                )
            }

            // With higher version it should be found
            assertTrue {
                filterMatches(
                    Exists(TestMarykModel { string::ref }),
                    HLC(lastVersions.first() + 1u)
                )
            }
        }

        assertFalse {
            filterMatches(
                Exists(TestMarykModel { selfReference::ref })
            )
        }
    }

    private fun doEqualsFilter() {
        assertTrue {
            filterMatches(
                Equals(TestMarykModel { string::ref } with "haha1")
            )
        }

        if (dataStore.keepAllVersions) {
            assertFalse {
                filterMatches(
                    Equals(TestMarykModel { string::ref } with "haha1"),
                    HLC(lastVersions.first() - 1u)
                )
            }

            // With higher version it should be found
            assertTrue {
                filterMatches(
                    Equals(TestMarykModel { string::ref } with "haha1"),
                    HLC(lastVersions.first() + 1u)
                )
            }
        }

        assertFalse {
            filterMatches(
                Equals(TestMarykModel { string::ref } with "wrong")
            )
        }
    }

    private fun doComplexMapListSetFilter() {
        assertTrue {
            filterMatches(
                Equals(TestMarykModel { map.refAt(Time(12, 13, 14)) } with "haha10")
            )
        }

        assertTrue {
            filterMatches(
                Equals(TestMarykModel { map.refToAny() } with "haha10")
            )
        }

        if (dataStore.keepAllVersions) {
            assertFalse {
                filterMatches(
                    Equals(TestMarykModel { map.refToAny() } with "haha10"),
                    HLC(lastVersions.first() - 1u)
                )
            }

            assertTrue {
                filterMatches(
                    Equals(TestMarykModel { map.refToAny() } with "haha10"),
                    HLC(lastVersions.last() + 1u)
                )
            }
        }

        assertFalse {
            filterMatches(
                Equals(TestMarykModel { map.refToAny() } with "haha11"),
                null
            )
        }

        assertFalse {
            filterMatches(
                Equals(TestMarykModel { map.refAt(Time(13, 13, 14)) } with "haha10")
            )
        }

        assertTrue {
            filterMatches(
                Equals(TestMarykModel { list refAt 1u } with 6)
            )
        }

        assertFalse {
            filterMatches(
                Equals(TestMarykModel { list refAt 2u } with 6)
            )
        }

        assertTrue {
            filterMatches(
                Equals(TestMarykModel { list.refToAny() } with 6)
            )
        }

        assertFalse {
            filterMatches(
                Equals(TestMarykModel { list.refToAny() } with 2)
            )
        }

        assertTrue {
            filterMatches(
                Equals(TestMarykModel { list refAt 1u } with 6)
            )
        }

        assertTrue {
            filterMatches(
                Exists(TestMarykModel { set refAt LocalDate(2018, 9, 9) })
            )
        }

        assertFalse {
            filterMatches(
                Exists(TestMarykModel { set refAt LocalDate(2017, 9, 9) })
            )
        }
    }

    private fun doPrefixFilter() {
        assertTrue {
            filterMatches(
                Prefix(TestMarykModel { string::ref } with "ha")
            )
        }

        assertFalse {
            filterMatches(
                Prefix(TestMarykModel { string::ref } with "wrong")
            )
        }
    }

    private fun doLessThanFilter() {
        assertTrue {
            filterMatches(
                LessThan(TestMarykModel { int::ref } with 6)
            )
        }

        assertFalse {
            filterMatches(
                LessThan(TestMarykModel { int::ref } with 5)
            )
        }

        assertFalse {
            filterMatches(
                LessThan(TestMarykModel { int::ref } with 2)
            )
        }
    }

    private fun doLessThanEqualsFilter() {
        assertTrue {
            filterMatches(
                LessThanEquals(TestMarykModel { int::ref } with 6)
            )
        }

        assertTrue {
            filterMatches(
                LessThanEquals(TestMarykModel { int::ref } with 5)
            )
        }

        assertFalse {
            filterMatches(
                LessThanEquals(TestMarykModel { int::ref } with 2)
            )
        }
    }

    private fun doGreaterThanFilter() {
        assertTrue {
            filterMatches(
                GreaterThan(TestMarykModel { int::ref } with 4)
            )
        }

        assertFalse {
            filterMatches(
                GreaterThan(TestMarykModel { int::ref } with 5)
            )
        }

        assertFalse {
            filterMatches(
                GreaterThan(TestMarykModel { int::ref } with 6)
            )
        }
    }

    private fun doGreaterThanEqualsFilter() {
        assertTrue {
            filterMatches(
                GreaterThanEquals(TestMarykModel { int::ref } with 4)
            )
        }

        assertTrue {
            filterMatches(
                GreaterThanEquals(TestMarykModel { int::ref } with 5)
            )
        }

        assertFalse {
            filterMatches(
                GreaterThanEquals(TestMarykModel { int::ref } with 6)
            )
        }
    }

    private fun doRangeFilter() {
        assertTrue {
            filterMatches(
                Range(TestMarykModel { int::ref } with (2..8))
            )
        }

        assertTrue {
            filterMatches(
                Range(TestMarykModel { int::ref } with (2..5))
            )
        }

        assertFalse {
            filterMatches(
                Range(TestMarykModel { int::ref } with (2..3))
            )
        }
    }

    private fun doRegExFilter() {
        assertTrue {
            filterMatches(
                RegEx(TestMarykModel { string::ref } with Regex("^h.*$"))
            )
        }

        assertFalse {
            filterMatches(
                RegEx(TestMarykModel { string::ref } with Regex("^b.*$"))
            )
        }
    }

    private fun doValueInFilter() {
        assertTrue {
            filterMatches(
                ValueIn(TestMarykModel { string::ref } with setOf("haha1", "haha2"))
            )
        }

        assertFalse {
            filterMatches(
                ValueIn(TestMarykModel { string::ref } with setOf("no1", "no2"))
            )
        }
    }

    private fun doNotFilter() {
        assertFalse {
            filterMatches(
                Not(Exists(TestMarykModel { string::ref }))
            )
        }

        assertTrue {
            filterMatches(
                Not(Exists(TestMarykModel { selfReference::ref }))
            )
        }
    }

    private fun doAndFilter() {
        assertTrue {
            filterMatches(
                And(
                    Exists(TestMarykModel { int::ref }),
                    Exists(TestMarykModel { string::ref })
                )
            )
        }

        assertFalse {
            filterMatches(
                And(
                    Exists(TestMarykModel { selfReference::ref }),
                    Exists(TestMarykModel { string::ref })
                )
            )
        }
    }

    private fun doOrFilter() {
        assertTrue {
            filterMatches(
                Or(
                    Exists(TestMarykModel { int::ref }),
                    Exists(TestMarykModel { string::ref })
                )
            )
        }

        assertTrue {
            filterMatches(
                Or(
                    Exists(TestMarykModel { selfReference::ref }),
                    Exists(TestMarykModel { string::ref })
                )
            )
        }

        assertFalse {
            filterMatches(
                Or(
                    Exists(TestMarykModel { selfReference::ref }),
                    Not(Exists(TestMarykModel { string::ref }))
                )
            )
        }
    }

    private fun doReferencedEqualsFilter() {
        assertTrue {
            filterMatches(
                Equals(TestMarykModel { reference { string::ref } } with "haha2")
            )
        }

        if (dataStore.keepAllVersions) {
            assertFalse {
                filterMatches(
                    Equals(TestMarykModel { reference { string::ref } } with "haha2"),
                    HLC(lastVersions.first() - 1u)
                )
            }

            // With higher version it should be found
            assertTrue {
                filterMatches(
                    Equals(TestMarykModel { reference { string::ref } } with "haha2"),
                    HLC(lastVersions.last() + 1u)
                )
            }
        }

        assertFalse {
            filterMatches(
                Equals(TestMarykModel { reference { string::ref } } with "wrong")
            )
        }
    }
}
