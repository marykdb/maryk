@file:Suppress("EXPERIMENTAL_UNSIGNED_LITERALS", "EXPERIMENTAL_API_USAGE")

package maryk.core.processors.datastore

import maryk.core.query.changes.Change
import maryk.core.query.changes.Delete
import maryk.core.query.changes.VersionedChanges
import maryk.core.query.pairs.with
import maryk.lib.extensions.initByteArrayByHex
import maryk.lib.time.DateTime
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
    "39" to (1235uL to V1)
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
                    Change(TestMarykModel.ref { dateTime } with DateTime(2018, 7, 18))
                )
            ),
            VersionedChanges(
                1234UL,
                listOf(
                    Change(
                        TestMarykModel.ref { string } with "hello world",
                        TestMarykModel.ref { int } with 5
                    )
                )
            ),
            VersionedChanges(
                1235UL,
                listOf(
                    Change(
                        TestMarykModel.ref { uint } with 3u,
                        TestMarykModel.ref { enum } with V1
                    )
                )
            )
        )
    }
}
