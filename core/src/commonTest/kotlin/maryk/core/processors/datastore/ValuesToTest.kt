package maryk.core.processors.datastore

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import maryk.core.properties.types.invoke
import maryk.test.models.EmbeddedMarykModel
import maryk.test.models.Option.V1
import maryk.test.models.SimpleMarykTypeEnum.S1
import maryk.test.models.TestMarykModel

val testMaryk = TestMarykModel(
    string = "hello world",
    int = 5,
    uint = 3u,
    double = 2.3,
    dateTime = LocalDateTime(2018, 7, 18, 0, 0),
    listOfString = listOf(
        "v1", "v2", "v3"
    ),
    set = setOf(
        LocalDate(2018, 9, 9),
        LocalDate(1981, 12, 5),
        LocalDate(1989, 5, 15)
    ),
    map = mapOf(
        LocalTime(11, 22, 33) to "eleven",
        LocalTime(12, 23, 34) to "twelve"
    ),
    embeddedValues = EmbeddedMarykModel(
        value = "test",
        model = EmbeddedMarykModel(
            value = "another test"
        )
    ),
    multi = S1("test"),
    setOfString = setOf(
        "abc", "def", "ghi"
    )
)

val valuesAsStorables = arrayOf(
    "09" to "hello world",
    "11" to 5,
    "19" to 3u,
    "21" to 2.3,
    "29" to LocalDateTime(2018, 7, 18, 0, 0),
    "39" to V1,
    "4b" to 3,
    "4b0480001104" to LocalDate(1981, 12, 5),
    "4b0480001ba2" to LocalDate(1989, 5, 15),
    "4b0480004577" to LocalDate(2018, 9, 9),
    "54" to 2,
    "5403009ff9" to "eleven",
    "540300ae46" to "twelve",
    "66" to Unit,
    "6609" to "test",
    "6616" to Unit,
    "661609" to "another test",
    "69" to S1( "test"),
    "7a" to 3,
    "7a00000000" to "v1",
    "7a00000001" to "v2",
    "7a00000002" to "v3",
    "8b01" to 3,
    "8b0103616263" to "abc",
    "8b0103646566" to "def",
    "8b0103676869" to "ghi"
)

val valuesAsStorablesWithNulls = arrayOf(
    "4b" to 1,
    "4b0480004577" to null,
    "4b0480001104" to LocalDate(1981, 12, 5),
    "4b0480001ba2" to null,
    "54" to 1,
    "5403009ff9" to null,
    "540300ae46" to "twelve",
    "66" to Unit,
    "6609" to "test",
    "661609" to null,
    "7a" to 1,
    "7a00000000" to "v1",
    "7a00000001" to null,
    "8b01" to 1,
    "8b0103616263" to null,
    "8b0103646566" to "def",
    "8b0103676869" to null
)
