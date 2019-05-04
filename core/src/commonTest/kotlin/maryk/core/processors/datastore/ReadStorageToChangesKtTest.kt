package maryk.core.processors.datastore

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
import maryk.test.shouldBe
import kotlin.test.Test

val valuesAsStorablesWithVersion = arrayOf(
    "09" to arrayOf(1234uL to "hello world", 1235uL to "hello universe"),
    "11" to arrayOf(1234uL to 5, 1235uL to 7),
    "19" to arrayOf(1235uL to 3u),
    "21" to arrayOf(1233uL to null),
    "29" to arrayOf(1233uL to DateTime(2018, 7, 18), 1235uL to null),
    "39" to arrayOf(1234uL to V2, 1235uL to V1),
    "4b" to arrayOf(1233uL to 1, 1235uL to 2, 1236uL to 2),
    "4b0480004577" to arrayOf(1233uL to Date(2018, 9, 9)),
    "4b0480001104" to arrayOf(1235uL to Date(1981, 12, 5)),
    "4b0480001105" to arrayOf(1235uL to Date(1981, 12, 6), 1236uL to null),
    "4b0480001ba2" to arrayOf(1235uL to null),
    "4b0480001ba3" to arrayOf(1235uL to null),
    "54" to arrayOf(1234uL to 3, 1236uL to null),
    "5403008fe9" to arrayOf(1233uL to "ten", 1235uL to null),
    "5403009ff9" to arrayOf(1234uL to "eleven", 1236uL to null),
    "5403009fe9" to arrayOf(1234uL to null),
    "540300ae46" to arrayOf(1234uL to "twelve", 1236uL to null),
    "540300ac46" to arrayOf(1234uL to null),
    "66" to arrayOf(1233uL to Unit, 1236uL to null),
    "6609" to arrayOf(1234uL to "test", 1236uL to null),
    "6616" to arrayOf(1234uL to Unit),
    "661609" to arrayOf(1234uL to "another test", 1235uL to null),
    "7a" to arrayOf(1234uL to 3, 1235uL to 2),
    "7a00000000" to arrayOf(1233uL to "v1"),
    "7a00000001" to arrayOf(1234uL to "v2"),
    "7a00000002" to arrayOf(1234uL to "v3", 1235uL to null),
    "7a00000003" to arrayOf(1234uL to null),
    "7a00000004" to arrayOf(1234uL to null),
    "8b01" to arrayOf(1233uL to 2, 1235uL to 1),
    "8b0103616263" to arrayOf(1233uL to "abc", 1235uL to null),
    "8b0103646566" to arrayOf(1233uL to "def", 1235uL to null),
    "8b0103676869" to arrayOf(1235uL to "ghi")
)

class ReadStorageToChangesKtTest {
    @Test
    fun convertStorageToChanges() {
        var qualifierIndex = -1
        val values = TestMarykModel.readStorageToChanges(
            getQualifier = {
                valuesAsStorablesWithVersion.getOrNull(++qualifierIndex)?.let {
                    initByteArrayByHex(it.first)
                }
            },
            select = null,
            processValue = { _, _, changer ->
                valuesAsStorablesWithVersion[qualifierIndex].second.forEach {
                    changer(it.first, it.second)
                }
            }
        )

        values shouldBe listOf(
            VersionedChanges(
                1233UL,
                listOf(
                    Delete(TestMarykModel.ref { double }),
                    Change(
                        TestMarykModel.ref { dateTime } with DateTime(2018, 7, 18),
                        TestMarykModel { map refAt Time(10, 14, 1) } with "ten",
                        TestMarykModel { listOfString refAt 0u } with "v1"
                    ),
                    SetChange(
                        TestMarykModel.ref { set }.change(
                            addValues = setOf(Date(2018, 9, 9))
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
                        TestMarykModel { map refAt Time(11, 22, 33) } with "eleven",
                        TestMarykModel { map refAt Time(12, 23, 34) } with "twelve",
                        TestMarykModel { embeddedValues.ref { value } } with "test",
                        TestMarykModel { embeddedValues { model.ref { value } } } with "another test",
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
                        TestMarykModel.ref { string } with "hello universe",
                        TestMarykModel.ref { int } with 7,
                        TestMarykModel.ref { uint } with 3u,
                        TestMarykModel.ref { enum } with V1
                    ),
                    Delete(
                        TestMarykModel.ref { dateTime },
                        TestMarykModel { set refAt Date(1989, 5, 15) },
                        TestMarykModel { set refAt Date(1989, 5, 16) },
                        TestMarykModel { map refAt Time(10, 14, 1) },
                        TestMarykModel { embeddedValues { model.ref { value } } },
                        TestMarykModel { listOfString.refAt(2u) },
                        TestMarykModel { setOfString refAt "abc" },
                        TestMarykModel { setOfString refAt "def" }
                    ),
                    SetChange(
                        TestMarykModel.ref { set }.change(
                            addValues = setOf(Date(1981, 12, 5), Date(1981, 12, 6))
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
                    Delete(
                        TestMarykModel { set refAt Date(1981, 12, 6) },
                        TestMarykModel.ref { map },
                        TestMarykModel.ref { embeddedValues }
                    )
                )
            )
        )
    }
}
