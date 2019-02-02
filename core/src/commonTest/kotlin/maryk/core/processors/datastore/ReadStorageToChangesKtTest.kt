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
import maryk.test.models.TestMarykModel
import maryk.test.shouldBe
import kotlin.test.Test

val valuesAsStorablesWithVersion = arrayOf<Pair<String, Pair<ULong, Any?>>>(
    "09" to (1234uL to "hello world"),
    "11" to (1234uL to 5),
    "19" to (1235uL to 3u),
    "21" to (1233uL to null),
    "29" to (1233uL to DateTime(2018, 7, 18)),
    "39" to (1235uL to V1),
    "4b" to (1235uL to 2),
    "4b80004577" to (1233uL to Date(2018, 9, 9)),
    "4b80001104" to (1235uL to Date(1981, 12, 5)),
    "4b80001105" to (1235uL to Date(1981, 12, 6)),
    "4b80001ba2" to (1235uL to null),
    "4b80001ba3" to (1235uL to null),
    "54" to (1234uL to 3),
    "5403008fe9" to (1233uL to "ten"),
    "5403009ff9" to (1234uL to "eleven"),
    "5403009fe9" to (1234uL to null),
    "540300ae46" to (1234uL to "twelve"),
    "540300ac46" to (1234uL to null),
    "6609" to (1234uL to "test"),
    "661609" to (1234uL to "another test"),
    "7a" to (1234uL to 3),
    "7a00000000" to (1233uL to "v1"),
    "7a00000001" to (1234uL to "v2"),
    "7a00000002" to (1234uL to "v3"),
    "7a00000003" to (1234uL to null),
    "7a00000004" to (1234uL to null)
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
            processValue = { _, _ , changer ->
                valuesAsStorablesWithVersion[qualifierIndex].second.apply {
                    changer(first, second)
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
                            addValues = setOf(Date(2018,9, 9))
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
                        TestMarykModel.ref { uint } with 3u,
                        TestMarykModel.ref { enum } with V1
                    ),
                    SetChange(
                        TestMarykModel.ref { set }.change(
                            addValues = setOf(Date(1981,12, 5), Date(1981,12, 6))
                        )
                    ),
                    Delete(
                        TestMarykModel { set refAt Date(1989, 5, 15) },
                        TestMarykModel { set refAt Date(1989, 5, 16) }
                    )
                )
            )
        )
    }
}
