package maryk.core.processors.datastore

import maryk.core.clock.HLC
import maryk.core.query.changes.Change
import maryk.core.query.changes.Delete
import maryk.core.query.changes.SetChange
import maryk.core.query.changes.VersionedChanges
import maryk.core.query.changes.change
import maryk.core.query.pairs.with
import maryk.lib.extensions.initByteArrayByHex
import maryk.lib.time.Date
import maryk.lib.time.DateTime
import maryk.lib.time.Time
import maryk.test.models.Option.V1
import maryk.test.models.Option.V2
import maryk.test.models.TestMarykModel
import kotlin.test.Test
import kotlin.test.assertEquals

private val valuesAsStorablesWithVersion = arrayOf(
    "09" to arrayOf(HLC(1234uL) to "hello world", HLC(1235uL) to "hello universe"),
    "11" to arrayOf(HLC(1234uL) to 5, HLC(1235uL) to 7),
    "19" to arrayOf(HLC(1235uL) to 3u),
    "21" to arrayOf(HLC(1233uL) to null),
    "29" to arrayOf(HLC(1233uL) to DateTime(2018, 7, 18), HLC(1235uL) to null),
    "39" to arrayOf(HLC(1234uL) to V2, HLC(1235uL) to V1),
    "4b" to arrayOf(HLC(1233uL) to 1, HLC(1235uL) to 2, HLC(1236uL) to 2),
    "4b0480004577" to arrayOf(HLC(1233uL) to Date(2018, 9, 9)),
    "4b0480001104" to arrayOf(HLC(1235uL) to Date(1981, 12, 5)),
    "4b0480001105" to arrayOf(HLC(1235uL) to Date(1981, 12, 6), HLC(1236uL) to null),
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
                    initByteArrayByHex(it.first)
                }
                qualifier?.let { resultHandler({ qualifier[it] }, qualifier.size); true } ?: false
            },
            select = null,
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
                        Delete(TestMarykModel { double::ref }),
                        Change(
                            TestMarykModel { dateTime::ref } with DateTime(2018, 7, 18),
                            TestMarykModel { map refAt Time(10, 14, 1) } with "ten",
                            TestMarykModel { listOfString refAt 0u } with "v1"
                        ),
                        SetChange(
                            TestMarykModel { set::ref }.change(
                                addValues = setOf(Date(2018, 9, 9))
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
                            TestMarykModel { map refAt Time(11, 22, 33) } with "eleven",
                            TestMarykModel { map refAt Time(12, 23, 34) } with "twelve",
                            TestMarykModel { embeddedValues { value::ref } } with "test",
                            TestMarykModel { embeddedValues { model { value::ref } } } with "another test",
                            TestMarykModel { listOfString refAt 1u } with "v2",
                            TestMarykModel { listOfString refAt 2u } with "v3"
                        ),
                        Delete(
                            TestMarykModel { map refAt Time(11, 22, 17) },
                            TestMarykModel { map refAt Time(12, 15, 2) },
                            TestMarykModel { listOfString refAt 3u },
                            TestMarykModel { listOfString refAt 4u }
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
                            TestMarykModel { enum::ref } with V1
                        ),
                        Delete(
                            TestMarykModel { dateTime::ref },
                            TestMarykModel { set refAt Date(1989, 5, 15) },
                            TestMarykModel { set refAt Date(1989, 5, 16) },
                            TestMarykModel { map refAt Time(10, 14, 1) },
                            TestMarykModel { embeddedValues { model { value::ref } } },
                            TestMarykModel { listOfString.refAt(2u) },
                            TestMarykModel { setOfString refAt "abc" },
                            TestMarykModel { setOfString refAt "def" }
                        ),
                        SetChange(
                            TestMarykModel { set::ref }.change(
                                addValues = setOf(Date(1981, 12, 5), Date(1981, 12, 6))
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
                        Delete(
                            TestMarykModel { set refAt Date(1981, 12, 6) },
                            TestMarykModel { map::ref },
                            TestMarykModel { embeddedValues::ref }
                        )
                    )
                )
            ),
            values
        )
    }
}
