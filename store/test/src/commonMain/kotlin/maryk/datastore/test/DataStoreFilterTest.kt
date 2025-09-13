package maryk.datastore.test

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
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
import maryk.core.query.responses.statuses.DeleteSuccess
import maryk.datastore.shared.IsDataStore
import maryk.test.models.TestMarykModel
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

    private val dataObject = TestMarykModel.create {
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
    }

    private val dataObject2 = TestMarykModel.create {
        string with "haha2"
        int with 3
        uint with 5u
        double with 0.23
        dateTime with LocalDateTime(2016, 2, 20, 0, 0)
        bool with false
        map with mapOf(
            LocalTime(17, 16, 15) to "haha1"
        )
        list with listOf(
            3, 4, 6
        )
        set with setOf(
            LocalDate(2020, 2, 20), LocalDate(2013, 4, 19)
        )
    }

    override suspend fun initData() {
        val addResponse = dataStore.execute(
            TestMarykModel.add(
                dataObject,
                dataObject2
            )
        )

        addResponse.statuses.forEach { status ->
            val response = assertStatusIs<AddSuccess<TestMarykModel>>(status)
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
            val response = assertStatusIs<ChangeSuccess<TestMarykModel>>(status)
            lastVersions.add(response.version)
        }

        firstKey = keys[0]
    }

    override suspend fun resetData() {
        dataStore.execute(
            TestMarykModel.delete(*keys.toTypedArray(), hardDelete = true)
        ).statuses.forEach {
            assertStatusIs<DeleteSuccess<*>>(it)
        }
        keys.clear()
        lastVersions.clear()
    }

    private suspend fun filterMatches(filter: IsFilter, hlc: HLC? = null): Boolean {
        val response = dataStore.execute(
            TestMarykModel.get(
                firstKey,
                where = filter,
                toVersion = hlc?.timestamp
            )
        )
        return response.values.isNotEmpty()
    }

    private suspend fun doExistsFilter() {
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

    private suspend fun doEqualsFilter() {
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

    private suspend fun doComplexMapListSetFilter() {
        assertTrue {
            filterMatches(
                Equals(TestMarykModel { map.refAt(LocalTime(12, 13, 14)) } with "haha10")
            )
        }

        if (dataStore.supportsFuzzyQualifierFiltering) {
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
        }

        assertFalse {
            filterMatches(
                Equals(TestMarykModel { map.refAt(LocalTime(13, 13, 14)) } with "haha10")
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

        if (dataStore.supportsFuzzyQualifierFiltering) {
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

    private suspend fun doPrefixFilter() {
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

    private suspend fun doLessThanFilter() {
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

    private suspend fun doLessThanEqualsFilter() {
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

    private suspend fun doGreaterThanFilter() {
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

    private suspend fun doGreaterThanEqualsFilter() {
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

    private suspend fun doRangeFilter() {
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

    private suspend fun doRegExFilter() {
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

    private suspend fun doValueInFilter() {
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

    private suspend fun doNotFilter() {
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

    private suspend fun doAndFilter() {
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

    private suspend fun doOrFilter() {
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

    private suspend fun doReferencedEqualsFilter() {
        if (dataStore.supportsSubReferenceFiltering) {
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
}
