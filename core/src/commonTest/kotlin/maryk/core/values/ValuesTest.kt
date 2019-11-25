package maryk.core.values

import maryk.lib.time.DateTime
import maryk.lib.time.Time
import maryk.test.models.EmbeddedMarykModel
import maryk.test.models.Option.V2
import maryk.test.models.TestMarykModel
import kotlin.test.Test
import kotlin.test.expect

class ValuesTest {
    @Test
    fun copy() {
        val original = TestMarykModel(
            string = "hello world",
            int = 5,
            uint = 3u,
            double = 2.3,
            dateTime = DateTime(2018, 7, 18)
        )

        val copy = original.copy {
            arrayOf(
                string withNotNull "bye world",
                enum withNotNull V2
            )
        }

        expect(6) { copy.size }
        expect("bye world") { copy { string } }
        expect(V2) { copy { enum } }
    }

    @Test
    fun testGetValue() {
        val values = TestMarykModel(
            string = "hello world",
            int = 5,
            uint = 3u,
            double = 2.3,
            dateTime = DateTime(2018, 7, 18),
            listOfString = listOf(
                "v1", "v2", "v3"
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

        expect("hello world") { values[TestMarykModel { string::ref }] }
        expect(2.3) { values[TestMarykModel { double::ref }] }
        expect(DateTime(2018, 7, 18)) { values[TestMarykModel { dateTime::ref }] }
        expect("v3") { values[TestMarykModel { listOfString refAt 2u }] }
        expect(listOf("v1", "v2", "v3")) { values[TestMarykModel { listOfString.refToAny() }] }
        expect("twelve") { values[TestMarykModel { map refAt Time(12, 23, 34) }] }
        expect("test") { values[TestMarykModel { embeddedValues { value::ref } }] }
        expect("another test") { values[TestMarykModel { embeddedValues { model { value::ref } } }] }
    }

    @Test
    fun testToJson() {
        val values = TestMarykModel(
            string = "hello world",
            int = 5,
            uint = 3u,
            double = 2.3,
            dateTime = DateTime(2018, 7, 18),
            listOfString = listOf(
                "v1", "v2", "v3"
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

        expect("""
        {
          "string": "hello world",
          "int": 5,
          "uint": 3,
          "double": "2.3",
          "dateTime": "2018-07-18T00:00",
          "enum": "V1(1)",
          "map": {
            "11:22:33": "eleven",
            "12:23:34": "twelve"
          },
          "embeddedValues": {
            "value": "test",
            "model": {
              "value": "another test"
            }
          },
          "listOfString": ["v1", "v2", "v3"]
        }
        """.trimIndent()) {
            values.toJson(pretty = true)
        }
    }
}
