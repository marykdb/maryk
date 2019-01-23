@file:Suppress("EXPERIMENTAL_UNSIGNED_LITERALS")

package maryk.core.processors.datastore

import maryk.core.properties.types.TypedValue
import maryk.lib.time.Date
import maryk.lib.time.DateTime
import maryk.lib.time.Time
import maryk.test.models.EmbeddedMarykModel
import maryk.test.models.Option.V1
import maryk.test.models.TestMarykModel

val testMaryk = TestMarykModel(
    string = "hello world",
    int = 5,
    uint = 3u,
    double = 2.3,
    dateTime = DateTime(2018, 7, 18),
    listOfString = listOf(
        "v1", "v2", "v3"
    ),
    set = setOf(
        Date(2018, 9, 9),
        Date(1981, 12, 5),
        Date(1989, 5, 15)
    ),
    map = mapOf(
        Time(11, 22, 33) to "eleven",
        Time(12, 23, 34) to "twelve"
    ),
    embeddedValues = EmbeddedMarykModel(
        value = "test",
        model = EmbeddedMarykModel(
            value = "another test"
        )
    ),
    multi = TypedValue(V1, "test"),
    setOfString = setOf(
        "abc", "def", "ghi"
    )
)

val valuesAsStorables = arrayOf(
    "09" to "hello world",
    "11" to 5,
    "19" to 3u,
    "21" to 2.3,
    "29" to DateTime(2018, 7, 18),
    "39" to V1,
    "4b" to 3,
    "4b80001104" to Date(1981, 12, 5),
    "4b80001ba2" to Date(1989, 5, 15),
    "4b80004577" to Date(2018, 9, 9),
    "54" to 2,
    "5403009ff9" to "eleven",
    "540300ae46" to "twelve",
    "66" to Unit,
    "6609" to "test",
    "6616" to Unit,
    "661609" to "another test",
    "69" to TypedValue(V1, "test"),
    "7a" to 3,
    "7a00000000" to "v1",
    "7a00000001" to "v2",
    "7a00000002" to "v3",
    "8b01" to 3,
    "8b01616263" to "abc",
    "8b01646566" to "def",
    "8b01676869" to "ghi"
)

val valuesAsStorablesWithNulls = arrayOf<Pair<String, Any?>>(
    "4b" to 1,
    "4b80004577" to null,
    "4b80001104" to Date(1981, 12, 5),
    "4b80001ba2" to null,
    "54" to 1,
    "5403009ff9" to null,
    "540300ae46" to "twelve",
    "6609" to "test",
    "661609" to null,
    "7a" to 1,
    "7a00000000" to "v1",
    "7a00000001" to null,
    "8b01" to 1,
    "8b01616263" to null,
    "8b01646566" to "def",
    "8b01676869" to null
)
