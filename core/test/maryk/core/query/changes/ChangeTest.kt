package maryk.core.query.changes

import maryk.core.properties.references.dsl.item

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.exceptions.RequestException
import maryk.core.properties.definitions.contextual.DataModelReference
import maryk.core.properties.references.ValueWithFlexBytesPropertyReference
import maryk.core.properties.types.invoke
import maryk.core.query.RequestContext
import maryk.core.query.pairs.with
import maryk.core.values.div
import maryk.test.models.SimpleMarykTypeEnum.S1
import maryk.test.models.TestMarykModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.expect

class ChangeTest {
    private val valueChange = Change(
        TestMarykModel.ref { string } with "test",
        TestMarykModel.ref { int } with 5
    )

    private val context = RequestContext(
        mapOf(
            TestMarykModel.Meta.name to DataModelReference(TestMarykModel),
        ),
        dataModel = TestMarykModel
    )

    @Test
    fun testValueChange() {
        expect(TestMarykModel.ref { string }) {
            valueChange.referenceValuePairs[0].reference as ValueWithFlexBytesPropertyReference<*, *, *, *>
        }
        expect("test") { valueChange.referenceValuePairs[0].value }
    }

    @Test
    fun convertToProtoBufAndBack() {
        checkProtoBufConversion(this.valueChange, Change, { this.context })
    }

    @Test
    fun convertToJSONAndBack() {
        checkJsonConversion(this.valueChange, Change, { this.context })
    }

    @Test
    fun convertToYAMLAndBack() {
        expect(
            """
            string: test
            int: 5

            """.trimIndent()
        ) {
            checkYamlConversion(this.valueChange, Change, { this.context })
        }
    }

    @Test
    fun changeValuesTest() {
        val original = TestMarykModel.create {
            string with "hello world"
            int with 5
            uint with 3u
            double with 2.3
            dateTime with LocalDateTime(2018, 7, 18, 0, 0)
            multi with S1( "world")
            list with listOf(3, 4, 5)
            set with setOf(LocalDate(2020, 2, 20), LocalDate(2019, 12, 11))
            map with mapOf(
                LocalTime(12, 0) to "Hi",
                LocalTime(1, 2) to "Hoi"
            )
            embeddedValues with {
                value with "hi"
                model with {
                    value with "bye"
                }
            }
        }

        var changed = original.change(listOf())

        assertEquals(original, changed)

        changed = original.change(
            Change(
                TestMarykModel.ref { string } with "hello universe"
            )
        )

        assertEquals("hello universe", changed { string })
        assertEquals("hello world", original { string })

        changed = original.change(
            Change(
                TestMarykModel.ref { multi.atType(S1) } with "universe"
            )
        )

        assertEquals("universe", changed { multi }?.value)
        assertEquals("world", original { multi }?.value)

        changed = original.change(
            Change(
                TestMarykModel.ref { list } with listOf(1, 2)
            )
        )

        assertEquals(listOf(1, 2), changed { list })
        assertEquals(listOf(3, 4, 5), original { list })

        changed = original.change(
            Change(
                TestMarykModel.ref { list.at(0u) } with 22
            )
        )

        assertEquals(22, changed { list }?.getOrNull(0))
        assertEquals(3, original { list }?.getOrNull(0))

        changed = original.change(
            Change(
                TestMarykModel.ref { list.any() } with 42
            )
        )

        assertEquals(listOf(42, 42, 42), changed { list })
        assertEquals(listOf(3, 4, 5), original { list })

        changed = original.change(
            Change(
                TestMarykModel.ref { map.at(LocalTime(12, 0)) } with "Bye"
            )
        )

        assertEquals(mapOf(LocalTime(12, 0) to "Bye", LocalTime(1, 2) to "Hoi"), changed { map })
        assertEquals(mapOf(LocalTime(12, 0) to "Hi", LocalTime(1, 2) to "Hoi"), original { map })

        changed = original.change(
            Change(
                TestMarykModel.ref { map.anyValue() } with "Hello"
            )
        )

        assertEquals(mapOf(LocalTime(12, 0) to "Hello", LocalTime(1, 2) to "Hello"), changed { map })
        assertEquals(mapOf(LocalTime(12, 0) to "Hi", LocalTime(1, 2) to "Hoi"), original { map })

        changed = original.change(
            Change(
                TestMarykModel.ref { embeddedValues { value } } with "bye"
            )
        )

        assertEquals("bye", changed { embeddedValues } / { value })
        assertEquals("hi", original { embeddedValues } / { value })

        changed = original.change(
            Change(
                TestMarykModel.ref { embeddedValues { model { value } } } with "goodbye"
            )
        )

        assertEquals("goodbye", changed { embeddedValues } / { model } / { value })
        assertEquals("bye", original { embeddedValues } / { model } / { value })

        assertFailsWith<RequestException> {
            // Cannot change non set sub values
            changed = original.change(
                Change(
                    TestMarykModel.ref { embeddedValues { marykModel { string } } } with "new"
                )
            )
        }
    }

    @Test
    fun changeValuesRemoveTest() {
        val original = TestMarykModel.create {
            string with "hello world"
            int with 5
            uint with 3u
            double with 2.3
            dateTime with LocalDateTime(2018, 7, 18, 0, 0)
            multi with S1("world")
            list with listOf(3, 4, 5)
            set with setOf(LocalDate(2020, 2, 20), LocalDate(2019, 12, 11))
            map with mapOf(
                LocalTime(12, 0) to "Hi",
                LocalTime(1, 2) to "Hoi"
            )
            embeddedValues with {
                value with "hi"
                model with { value with "bye" }
            }
        }

        var changed = original.change(listOf())

        assertEquals(original, changed)

        changed = original.change(
            Change(TestMarykModel.ref { int } with null)
        )

        assertNull(changed { int })
        assertEquals(5, original { int })

        changed = original.change(
            Change(TestMarykModel.ref { multi.atType(S1) } with null)
        )

        assertNull(changed { multi }?.value)
        assertEquals("world", original { multi }?.value)

        changed = original.change(
            Change(TestMarykModel.ref { list } with null)
        )

        assertNull(changed { list })
        assertEquals(listOf(3, 4, 5), original { list })

        changed = original.change(
            Change(TestMarykModel.ref { list.at(0u) } with null)
        )

        assertEquals(4, changed { list }?.getOrNull(0))
        assertEquals(3, original { list }?.getOrNull(0))

        changed = original.change(
            Change(TestMarykModel.ref { list.any() } with null)
        )

        assertEquals(emptyList(), changed { list })
        assertEquals(listOf(3, 4, 5), original { list })

        changed = original.change(
            Change(TestMarykModel.ref { set.item(LocalDate(2020, 2, 20)) } with null)
        )

        assertEquals(setOf(LocalDate(2019, 12, 11)), changed { set })
        assertEquals(setOf(LocalDate(2020, 2, 20), LocalDate(2019, 12, 11)), original { set })

        changed = original.change(
            Change(TestMarykModel.ref { map.at(LocalTime(12, 0)) } with null)
        )

        assertEquals(mapOf(LocalTime(1, 2) to "Hoi"), changed { map })
        assertEquals(mapOf(LocalTime(12, 0) to "Hi", LocalTime(1, 2) to "Hoi"), original { map })

        changed = original.change(
            Change(TestMarykModel.ref { map.anyValue() } with null)
        )

        assertEquals(emptyMap(), changed { map })
        assertEquals(mapOf(LocalTime(12, 0) to "Hi", LocalTime(1, 2) to "Hoi"), original { map })

        changed = original.change(
            Change(TestMarykModel.ref { embeddedValues { value } } with null)
        )

        assertNull(changed { embeddedValues } / { value })
        assertEquals("hi", original { embeddedValues } / { value })

        changed = original.change(
            Change(TestMarykModel.ref { embeddedValues { model { value } } } with null)
        )

        assertNull(changed { embeddedValues } / { model } / { value })
        assertEquals("bye", original { embeddedValues } / { model } / { value })

        changed = original.change(
            Change(TestMarykModel.ref { embeddedValues { marykModel { string } } } with null)
        )

        assertNull(changed { embeddedValues } / { marykModel } / { string })
        assertNull(original { embeddedValues } / { marykModel } / { string })
    }
}
