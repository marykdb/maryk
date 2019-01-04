@file:Suppress("EXPERIMENTAL_UNSIGNED_LITERALS", "EXPERIMENTAL_API_USAGE")

package maryk.core.processors.datastore

import maryk.core.query.changes.Change
import maryk.core.query.changes.Delete
import maryk.core.query.changes.MapChange
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
    "54008fe9" to (1233uL to "ten"),
    "54009ff9" to (1234uL to "eleven"),
    "54009fe9" to (1234uL to null),
    "5400ae46" to (1234uL to "twelve"),
    "5400ac46" to (1234uL to null)
)

class ConvertStorageToChangesKtTest {
    @Test
    fun convertStorageToChanges() {
        var qualifierIndex = -1
        val values = TestMarykModel.convertStorageToChanges(
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
                    Change(TestMarykModel.ref { dateTime } with DateTime(2018, 7, 18)),
                    SetChange(
                        TestMarykModel.ref { set }.change(
                            addValues = setOf(Date(2018,9, 9)),
                            deleteValues = setOf() // For comparison purposes
                        )
                    ),
                    MapChange(
                        TestMarykModel.ref { map }.change(
                            valuesToAdd = mapOf(Time(10, 14, 1) to "ten"),
                            keysToDelete = setOf()
                        )
                    )
                )
            ),
            VersionedChanges(
                1234UL,
                listOf(
                    Change(
                        TestMarykModel.ref { string } with "hello world",
                        TestMarykModel.ref { int } with 5
                    ),
                    MapChange(
                        TestMarykModel.ref { map }.change(
                            valuesToAdd = mapOf(
                                Time(11, 22, 33) to "eleven",
                                Time(12, 23, 34) to "twelve"
                            ),
                            keysToDelete = setOf(
                                Time(11, 22, 17), Time(12, 15, 2)
                            )
                        )
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
                            addValues = setOf(Date(1981,12, 5), Date(1981,12, 6)),
                            deleteValues = setOf(Date(1989, 5, 15), Date(1989, 5, 16))
                        )
                    )
                )
            )
        )
    }
}
