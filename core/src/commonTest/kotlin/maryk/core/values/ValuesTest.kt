package maryk.core.values

import maryk.core.exceptions.RequestException
import maryk.core.properties.graph.graph
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.types.Date
import maryk.core.properties.types.TypedValue
import maryk.core.query.changes.Change
import maryk.core.query.changes.ListChange
import maryk.core.query.changes.SetChange
import maryk.core.query.changes.change
import maryk.core.query.pairs.with
import maryk.core.query.requests.change
import maryk.lib.time.DateTime
import maryk.lib.time.Time
import maryk.test.models.EmbeddedMarykModel
import maryk.test.models.Option.V2
import maryk.test.models.SimpleMarykTypeEnum.S1
import maryk.test.models.TestMarykModel
import maryk.test.models.TestMarykModel.Properties
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
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
            dateTime = DateTime(2018, 7, 18)
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
            dateTime = DateTime(2018, 7, 18),
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

    @Test
    fun processValuesTest() {
        val values = TestMarykModel(
            string = "hello world",
            int = 5,
            uint = 3u,
            double = 2.3,
            dateTime = DateTime(2018, 7, 18),
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

    @Test
    fun changeTest() {
        val original = TestMarykModel(
            string = "hello world",
            int = 5,
            uint = 3u,
            double = 2.3,
            dateTime = DateTime(2018, 7, 18),
            multi = TypedValue(S1, "world"),
            list = listOf(3, 4, 5),
            set = setOf(Date(2020, 2, 20), Date(2019, 12, 11)),
            map = mapOf(
                Time(12, 0) to "Hi",
                Time(1, 2) to "Hoi"
            ),
            embeddedValues = EmbeddedMarykModel(
                value = "hi"
            )
        )

        var changed = original.change(listOf())

        assertEquals(original, changed)

        changed = original.change(
            Change(
                TestMarykModel { string::ref } with "hello universe"
            )
        )

        assertEquals("hello universe", changed { string })
        assertEquals("hello world", original { string })

        changed = original.change(
            Change(
                TestMarykModel { multi.refAtType(S1) } with "universe"
            )
        )

        assertEquals("universe", changed { multi }?.value)
        assertEquals("world", original { multi }?.value)

        changed = original.change(
            Change(
                TestMarykModel { list::ref } with listOf(1, 2)
            )
        )

        assertEquals(listOf(1, 2), changed { list })
        assertEquals(listOf(3, 4, 5), original { list })

        changed = original.change(
            Change(
                TestMarykModel { list.refAt(0u) } with 22
            )
        )

        assertEquals(22, changed { list }?.getOrNull(0))
        assertEquals(3, original { list }?.getOrNull(0))

        changed = original.change(
            Change(
                TestMarykModel { list::ref } with listOf(6, 7, 8)
            )
        )

        assertEquals(listOf(6, 7, 8), changed { list })
        assertEquals(listOf(3, 4, 5), original { list })

        changed = original.change(
            Change(
                TestMarykModel { list.refToAny() } with 42
            )
        )

        assertEquals(listOf(42, 42, 42), changed { list })
        assertEquals(listOf(3, 4, 5), original { list })

        changed = original.change(
            Change(
                TestMarykModel { map.refAt(Time(12, 0)) } with "Bye"
            )
        )

        assertEquals(mapOf(Time(12, 0) to "Bye", Time(1, 2) to "Hoi"), changed { map })
        assertEquals(mapOf(Time(12, 0) to "Hi", Time(1, 2) to "Hoi"), original { map })

        changed = original.change(
            Change(
                TestMarykModel { map.refToAny() } with "Hello"
            )
        )

        assertEquals(mapOf(Time(12, 0) to "Hello", Time(1, 2) to "Hello"), changed { map })
        assertEquals(mapOf(Time(12, 0) to "Hi", Time(1, 2) to "Hoi"), original { map })

        changed = original.change(
            Change(
                TestMarykModel { embeddedValues { value::ref } } with "bye"
            )
        )

        assertEquals("bye", changed { embeddedValues } / { value })
        assertEquals("hi", original { embeddedValues } / { value })

        assertFailsWith<RequestException> {
            // Cannot change non set sub values
            changed = original.change(
                Change(
                    TestMarykModel { embeddedValues { model { value::ref } } } with "new"
                )
            )
        }
    }

    @Test
    fun changeListTest() {
        val original = TestMarykModel(
            string = "hello world",
            int = 5,
            uint = 3u,
            double = 2.3,
            dateTime = DateTime(2018, 7, 18),
            list = listOf(3, 4, 5)
        )

        val changed = original.change(
            ListChange(
                TestMarykModel { list::ref }.change(
                    deleteValues = listOf(3),
                    addValuesAtIndex = mapOf(
                        1u to 999
                    ),
                    addValuesToEnd = listOf(8)
                )
            )
        )

        assertEquals(listOf(4, 999, 5, 8), changed { list })
        assertEquals(listOf(3, 4, 5), original { list })
    }

    @Test
    fun changeSetTest() {
        val original = TestMarykModel(
            string = "hello world",
            int = 5,
            uint = 3u,
            double = 2.3,
            dateTime = DateTime(2018, 7, 18),
            set = setOf(Date(2020, 2, 20), Date(2019, 12, 11))
        )

        val changed = original.change(
            SetChange(
                TestMarykModel { set::ref }.change(
                    addValues = setOf(
                        Date(1981, 12, 5), Date(1989, 5, 15)
                    )
                )
            )
        )

        assertEquals(
            setOf(Date(2020, 2, 20), Date(2019, 12, 11), Date(1981, 12, 5), Date(1989, 5, 15)),
            changed { set }
        )
        assertEquals(
            setOf(Date(2020, 2, 20), Date(2019, 12, 11)),
            original { set }
        )
    }
}
