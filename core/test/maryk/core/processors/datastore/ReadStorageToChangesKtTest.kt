package maryk.core.processors.datastore

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import maryk.core.clock.HLC
import maryk.core.query.changes.Change
import maryk.core.query.changes.ObjectCreate
import maryk.core.query.changes.SetChange
import maryk.core.query.changes.VersionedChanges
import maryk.core.query.changes.change
import maryk.core.query.pairs.with
import maryk.lib.exceptions.ParseException
import maryk.test.models.Option.V0
import maryk.test.models.Option.V2
import maryk.test.models.TestMarykModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

private val valuesAsStorablesWithVersion = arrayOf(
    "09" to arrayOf(HLC(1234uL) to "hello world", HLC(1235uL) to "hello universe"),
    "11" to arrayOf(HLC(1234uL) to 5, HLC(1235uL) to 7),
    "19" to arrayOf(HLC(1235uL) to 3u),
    "21" to arrayOf(HLC(1233uL) to null),
    "29" to arrayOf(HLC(1233uL) to LocalDateTime(2018, 7, 18, 0, 0), HLC(1235uL) to null),
    "39" to arrayOf(HLC(1234uL) to V2, HLC(1235uL) to V0),
    "4b" to arrayOf(HLC(1233uL) to 1, HLC(1235uL) to 2, HLC(1236uL) to 2),
    "4b0480004577" to arrayOf(HLC(1233uL) to LocalDate(2018, 9, 9)),
    "4b0480001104" to arrayOf(HLC(1235uL) to LocalDate(1981, 12, 5)),
    "4b0480001105" to arrayOf(HLC(1235uL) to LocalDate(1981, 12, 6), HLC(1236uL) to null),
    "4b0480001ba2" to arrayOf(HLC(1235uL) to null),
    "4b0480001ba3" to arrayOf(HLC(1235uL) to null),
    "54" to arrayOf(HLC(1234uL) to 3, HLC(1236uL) to null),
    "5403008fe9" to arrayOf(HLC(1233uL) to "ten", HLC(1235uL) to null),
    "5403009ff9" to arrayOf(HLC(1234uL) to "eleven", HLC(1236uL) to null),
    "5403009fe9" to arrayOf(HLC(1234uL) to null),
    "540300ae46" to arrayOf(HLC(1234uL) to "twelve", HLC(1236uL) to null),
    "540300ac46" to arrayOf(HLC(1234uL) to null),
    "66" to arrayOf(HLC(1233uL) to Unit, HLC(1236uL) to null),
    "6609" to arrayOf(HLC(1234uL) to "test", HLC(1236uL) to null),
    "6616" to arrayOf(HLC(1234uL) to Unit),
    "661609" to arrayOf(HLC(1234uL) to "another test", HLC(1235uL) to null),
    "7a" to arrayOf(HLC(1234uL) to 3, HLC(1235uL) to 2),
    "7a00000000" to arrayOf(HLC(1233uL) to "v1"),
    "7a00000001" to arrayOf(HLC(1234uL) to "v2"),
    "7a00000002" to arrayOf(HLC(1234uL) to "v3", HLC(1235uL) to null),
    "7a00000003" to arrayOf(HLC(1234uL) to null),
    "7a00000004" to arrayOf(HLC(1234uL) to null),
    "8b01" to arrayOf(HLC(1233uL) to 2, HLC(1235uL) to 1),
    "8b0103616263" to arrayOf(HLC(1233uL) to "abc", HLC(1235uL) to null),
    "8b0103646566" to arrayOf(HLC(1233uL) to "def", HLC(1235uL) to null),
    "8b0103676869" to arrayOf(HLC(1235uL) to "ghi")
)

class ReadStorageToChangesKtTest {
    @Test
    fun convertStorageToChanges() {
        var qualifierIndex = -1

        val values = TestMarykModel.readStorageToChanges(
            getQualifier = { resultHandler ->
                val qualifier = valuesAsStorablesWithVersion.getOrNull(++qualifierIndex)?.let {
                    (it.first).hexToByteArray()
                }
                qualifier?.let { resultHandler({ qualifier[it] }, qualifier.size); true } == true
            },
            select = null,
            creationVersion = 1233uL,
            processValue = { _, _, changer ->
                valuesAsStorablesWithVersion[qualifierIndex].second.forEach {
                    changer(it.first.timestamp, it.second)
                }
            }
        )

        assertEquals(
            listOf(
                VersionedChanges(
                    1233UL,
                    listOf(
                        ObjectCreate,
                        Change(
                            TestMarykModel.ref { double } with null,
                            TestMarykModel.ref { dateTime } with LocalDateTime(2018, 7, 18, 0, 0),
                            TestMarykModel.ref { map at LocalTime(10, 14, 1) } with "ten",
                            TestMarykModel.ref { listOfString at 0u } with "v1"
                        ),
                        SetChange(
                            TestMarykModel.ref { set }.change(
                                addValues = setOf(LocalDate(2018, 9, 9))
                            ),
                            TestMarykModel.ref { setOfString }.change(
                                addValues = setOf("abc", "def")
                            )
                        )
                    )
                ),
                VersionedChanges(
                    1234UL,
                    listOf(
                        Change(
                            TestMarykModel.ref { string } with "hello world",
                            TestMarykModel.ref { int } with 5,
                            TestMarykModel.ref { enum } with V2,
                            TestMarykModel.ref { map at LocalTime(11, 22, 33) } with "eleven",
                            TestMarykModel.ref { map at LocalTime(11, 22, 17) } with null,
                            TestMarykModel.ref { map at LocalTime(12, 23, 34) } with "twelve",
                            TestMarykModel.ref { map at LocalTime(12, 15, 2) } with null,
                            TestMarykModel.ref { embeddedValues { value } } with "test",
                            TestMarykModel.ref { embeddedValues { model { value } } } with "another test",
                            TestMarykModel.ref { listOfString at 1u } with "v2",
                            TestMarykModel.ref { listOfString at 2u } with "v3",
                            TestMarykModel.ref { listOfString at 3u } with null,
                            TestMarykModel.ref { listOfString at 4u } with null
                        )
                    )
                ),
                VersionedChanges(
                    1235UL,
                    listOf(
                        Change(
                            TestMarykModel.ref { string } with "hello universe",
                            TestMarykModel.ref { int } with 7,
                            TestMarykModel.ref { uint } with 3u,
                            TestMarykModel.ref { dateTime } with null,
                            TestMarykModel.ref { enum } with V0,
                            TestMarykModel.ref { set item LocalDate(1989, 5, 15) } with null,
                            TestMarykModel.ref { set item LocalDate(1989, 5, 16) } with null,
                            TestMarykModel.ref { map at LocalTime(10, 14, 1) } with null,
                            TestMarykModel.ref { embeddedValues { model { value } } } with null,
                            TestMarykModel.ref { listOfString.at(2u) } with null,
                            TestMarykModel.ref { setOfString item "abc" } with null,
                            TestMarykModel.ref { setOfString item "def" } with null,
                        ),
                        SetChange(
                            TestMarykModel.ref { set }.change(
                                addValues = setOf(LocalDate(1981, 12, 5), LocalDate(1981, 12, 6))
                            ),
                            TestMarykModel.ref { setOfString }.change(
                                addValues = setOf("ghi")
                            )
                        )
                    )
                ),
                VersionedChanges(
                    1236UL,
                    listOf(
                        Change(
                            TestMarykModel.ref { set item LocalDate(1981, 12, 6) } with null,
                            TestMarykModel.ref { map } with null,
                            TestMarykModel.ref { embeddedValues } with null
                        )
                    )
                )
            ),
            values
        )
    }

    @Test
    fun rejectsTruncatedSetQualifier() {
        var done = false
        assertFailsWith<ParseException> {
            TestMarykModel.readStorageToChanges(
                getQualifier = { resultHandler ->
                    if (done) {
                        false
                    } else {
                        done = true
                        val qualifier = "4b04".hexToByteArray()
                        resultHandler({ qualifier[it] }, qualifier.size)
                        true
                    }
                },
                select = null,
                creationVersion = null,
                processValue = { _, _, _ -> }
            )
        }
    }

    @Test
    fun rejectsTruncatedMapQualifier() {
        var done = false
        assertFailsWith<ParseException> {
            TestMarykModel.readStorageToChanges(
                getQualifier = { resultHandler ->
                    if (done) {
                        false
                    } else {
                        done = true
                        val qualifier = "5403".hexToByteArray()
                        resultHandler({ qualifier[it] }, qualifier.size)
                        true
                    }
                },
                select = null,
                creationVersion = null,
                processValue = { _, _, _ -> }
            )
        }
    }
}
