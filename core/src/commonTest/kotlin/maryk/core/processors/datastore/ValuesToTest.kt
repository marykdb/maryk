@file:Suppress("EXPERIMENTAL_UNSIGNED_LITERALS")

package maryk.core.processors.datastore

import maryk.lib.time.Date
import maryk.lib.time.DateTime
import maryk.lib.time.Time
import maryk.test.models.EmbeddedMarykModel
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
    )
)

val valuesAsStorables = arrayOf<Pair<String, Any>>(
    "09" to testMaryk { string }!!,
    "11" to testMaryk { int }!!,
    "19" to testMaryk { uint }!!,
    "21" to testMaryk { double }!!,
    "29" to testMaryk { dateTime }!!,
    "39" to testMaryk { enum }!!,
    "4b" to 3,
    "4b80004577" to Date(2018, 9, 9),
    "4b80001104" to Date(1981, 12, 5),
    "4b80001ba2" to Date(1989, 5, 15),
    "54" to 2,
    "54009ff9" to "eleven",
    "5400ae46" to "twelve",
    "6109" to testMaryk { embeddedValues }!!{ value }!!,
    "611109" to testMaryk { embeddedValues }!!{ model }!!{ value }!!,
    "7a" to 3,
    "7a00000000" to "v1",
    "7a00000001" to "v2",
    "7a00000002" to "v3"
)
