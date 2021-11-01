package maryk.core.values

import kotlinx.datetime.LocalDateTime
import maryk.core.properties.graph.graph
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.types.TypedValue
import maryk.lib.time.Time
import maryk.test.models.EmbeddedMarykModel
import maryk.test.models.Option.V2
import maryk.test.models.SimpleMarykTypeEnum.S1
import maryk.test.models.TestMarykModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.expect

class ValuesTest {
    @Test
    fun copy() {
        val original = TestMarykModel(
            string = "hello world",
            int = 5,
            uint = 3u,
            double = 2.3,
            dateTime = LocalDateTime(2018, 7, 18, 0, 0)
        )

        val copy = original.copy {
            listOf(
                string withNotNull "bye world",
                enum withNotNull V2
            )
        }

        expect(6) { copy.size }
        expect("bye world") { copy { string } }
        expect(V2) { copy { enum } }
    }

    @Test
    fun filterWithSelect() {
        val original = TestMarykModel(
            string = "hello world",
            int = 5,
            uint = 3u,
            double = 2.3,
            dateTime = LocalDateTime(2018, 7, 18, 0, 0),
            embeddedValues = EmbeddedMarykModel(
                value = "hello universe",
                model = EmbeddedMarykModel(
                    value = "hello multiverse"
                )
            )
        )

        val filtered = original.filterWithSelect(
            TestMarykModel.graph {
                listOf(
                    string,
                    uint,
                    graph(embeddedValues) {
                        listOf(
                            value
                        )
                    }
                )
            }
        )

        expect(3) { filtered.size }
        expect("hello world") { filtered { string } }
        assertNull(filtered { int })
        assertEquals("hello universe", filtered { embeddedValues } / { value })
        assertNull(filtered { embeddedValues } / { model })
    }

    @Test
    fun testGetValue() {
        val values = TestMarykModel(
            string = "hello world",
            int = 5,
            uint = 3u,
            double = 2.3,
            dateTime = LocalDateTime(2018, 7, 18, 0, 0),
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
        expect(LocalDateTime(2018, 7, 18, 0, 0)) { values[TestMarykModel { dateTime::ref }] }
        expect("v3") { values[TestMarykModel { listOfString refAt 2u }] }
        expect(listOf("v1", "v2", "v3")) { values[TestMarykModel { listOfString.refToAny() }] }
        expect("twelve") { values[TestMarykModel { map refAt Time(12, 23, 34) }] }
        expect("test") { values[TestMarykModel { embeddedValues { value::ref } }] }
        expect("another test") { values[TestMarykModel { embeddedValues { model { value::ref } } }] }
    }

    @Test
    fun testGetValueNotReified() {
        val values = TestMarykModel(
            string = "hello world",
            int = 5,
            uint = 3u,
            double = 2.3,
            dateTime = LocalDateTime(2018, 7, 18, 0, 0),
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

        expect("hello world") { values.get { string } }
        expect(2.3) { values.get { double } }
        expect(LocalDateTime(2018, 7, 18, 0, 0)) { values.get { dateTime } }
        expect("v3") { values.get { listOfString }?.get(2) }
        expect(listOf("v1", "v2", "v3")) { values.get { listOfString } }
        expect("twelve") { values.get { map }?.get(Time(12, 23, 34)) }
        expect("test") { values.get { embeddedValues }?.get { value } }
        expect("another test") { values.get { embeddedValues }?.get { model }?.get { value } }
    }

    @Test
    fun testToJson() {
        val values = TestMarykModel(
            string = "hello world",
            int = 5,
            uint = 3u,
            double = 2.3,
            dateTime = LocalDateTime(2018, 7, 18, 0, 0),
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

    @Test
    fun processValuesTest() {
        val values = TestMarykModel(
            string = "hello world",
            int = 5,
            uint = 3u,
            double = 2.3,
            dateTime = LocalDateTime(2018, 7, 18, 0, 0),
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
            multi = TypedValue(S1, "s1value"),
            listOfString = listOf(
                "v1", "v2", "v3"
            )
        )

        val expectedValues = arrayOf<Pair<IsPropertyReference<*, *, *>, Any?>>(
            TestMarykModel { string::ref } to values { string },
            TestMarykModel { int::ref } to values { int },
            TestMarykModel { uint::ref } to values { uint },
            TestMarykModel { double::ref } to values { double },
            TestMarykModel { dateTime::ref } to values { dateTime },
            TestMarykModel { enum::ref } to values { enum },
            TestMarykModel { map refAt Time(11, 22, 33) } to "eleven",
            TestMarykModel { map refAt Time(12, 23, 34) } to "twelve",
            TestMarykModel { embeddedValues { value::ref } } to "test",
            TestMarykModel { embeddedValues { model { value::ref } } } to "another test",
            TestMarykModel { multi.refAtType(S1) } to "s1value",
            TestMarykModel { listOfString refAt 0u } to "v1",
            TestMarykModel { listOfString refAt 1u } to "v2",
            TestMarykModel { listOfString refAt 2u } to "v3"
        )

        var index = 0
        values.processAllValues { propertyReference, value ->
            val expected = expectedValues[index++]
            assertEquals(expected.first, propertyReference)
            assertEquals(expected.second, value)
        }
    }
}
