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
import maryk.test.models.Option.V0
import maryk.test.models.Option.V2
import maryk.test.models.TestMarykModel
import kotlin.test.Test
import kotlin.test.assertEquals

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
                            TestMarykModel { double::ref } with null,
                            TestMarykModel { dateTime::ref } with LocalDateTime(2018, 7, 18, 0, 0),
                            TestMarykModel { map refAt LocalTime(10, 14, 1) } with "ten",
                            TestMarykModel { listOfString refAt 0u } with "v1"
                        ),
                        SetChange(
                            TestMarykModel { set::ref }.change(
                                addValues = setOf(LocalDate(2018, 9, 9))
                            ),
                            TestMarykModel { setOfString::ref }.change(
                                addValues = setOf("abc", "def")
                            )
                        )
                    )
                ),
                VersionedChanges(
                    1234UL,
                    listOf(
                        Change(
                            TestMarykModel { string::ref } with "hello world",
                            TestMarykModel { int::ref } with 5,
                            TestMarykModel { enum::ref } with V2,
                            TestMarykModel { map refAt LocalTime(11, 22, 33) } with "eleven",
                            TestMarykModel { map refAt LocalTime(11, 22, 17) } with null,
                            TestMarykModel { map refAt LocalTime(12, 23, 34) } with "twelve",
                            TestMarykModel { map refAt LocalTime(12, 15, 2) } with null,
                            TestMarykModel { embeddedValues { value::ref } } with "test",
                            TestMarykModel { embeddedValues { model { value::ref } } } with "another test",
                            TestMarykModel { listOfString refAt 1u } with "v2",
                            TestMarykModel { listOfString refAt 2u } with "v3",
                            TestMarykModel { listOfString refAt 3u } with null,
                            TestMarykModel { listOfString refAt 4u } with null
                        )
                    )
                ),
                VersionedChanges(
                    1235UL,
                    listOf(
                        Change(
                            TestMarykModel { string::ref } with "hello universe",
                            TestMarykModel { int::ref } with 7,
                            TestMarykModel { uint::ref } with 3u,
                            TestMarykModel { dateTime::ref } with null,
                            TestMarykModel { enum::ref } with V0,
                            TestMarykModel { set refAt LocalDate(1989, 5, 15) } with null,
                            TestMarykModel { set refAt LocalDate(1989, 5, 16) } with null,
                            TestMarykModel { map refAt LocalTime(10, 14, 1) } with null,
                            TestMarykModel { embeddedValues { model { value::ref } } } with null,
                            TestMarykModel { listOfString.refAt(2u) } with null,
                            TestMarykModel { setOfString refAt "abc" } with null,
                            TestMarykModel { setOfString refAt "def" } with null,
                        ),
                        SetChange(
                            TestMarykModel { set::ref }.change(
                                addValues = setOf(LocalDate(1981, 12, 5), LocalDate(1981, 12, 6))
                            ),
                            TestMarykModel { setOfString::ref }.change(
                                addValues = setOf("ghi")
                            )
                        )
                    )
                ),
                VersionedChanges(
                    1236UL,
                    listOf(
                        Change(
                            TestMarykModel { set refAt LocalDate(1981, 12, 6) } with null,
                            TestMarykModel { map::ref } with null,
                            TestMarykModel { embeddedValues::ref } with null
                        )
                    )
                )
            ),
            values
        )
    }
}
