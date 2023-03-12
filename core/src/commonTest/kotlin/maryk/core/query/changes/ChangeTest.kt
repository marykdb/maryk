package maryk.core.query.changes

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.exceptions.RequestException
import maryk.core.extensions.toUnitLambda
import maryk.core.properties.references.ValueWithFlexBytesPropertyReference
import maryk.core.properties.types.TypedValue
import maryk.core.query.RequestContext
import maryk.core.query.pairs.with
import maryk.core.values.div
import maryk.test.models.EmbeddedMarykModel
import maryk.test.models.SimpleMarykTypeEnum.S1
import maryk.test.models.TestMarykModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.expect

class ChangeTest {
    private val valueChange = Change(
        TestMarykModel { string::ref } with "test",
        TestMarykModel { int::ref } with 5
    )

    private val context = RequestContext(
        mapOf(
            TestMarykModel.Model.name toUnitLambda { TestMarykModel.Model }
        ),
        dataModel = TestMarykModel.Model
    )

    @Test
    fun testValueChange() {
        expect(TestMarykModel { string::ref }) {
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
        val original = TestMarykModel(
            string = "hello world",
            int = 5,
            uint = 3u,
            double = 2.3,
            dateTime = LocalDateTime(2018, 7, 18, 0, 0),
            multi = TypedValue(S1, "world"),
            list = listOf(3, 4, 5),
            set = setOf(LocalDate(2020, 2, 20), LocalDate(2019, 12, 11)),
            map = mapOf(
                LocalTime(12, 0) to "Hi",
                LocalTime(1, 2) to "Hoi"
            ),
            embeddedValues = EmbeddedMarykModel.run { create(
                value with "hi",
                model with EmbeddedMarykModel.run { create(
                    value with "bye"
                ) }
            ) }
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
                TestMarykModel { list.refToAny() } with 42
            )
        )

        assertEquals(listOf(42, 42, 42), changed { list })
        assertEquals(listOf(3, 4, 5), original { list })

        changed = original.change(
            Change(
                TestMarykModel { map.refAt(LocalTime(12, 0)) } with "Bye"
            )
        )

        assertEquals(mapOf(LocalTime(12, 0) to "Bye", LocalTime(1, 2) to "Hoi"), changed { map })
        assertEquals(mapOf(LocalTime(12, 0) to "Hi", LocalTime(1, 2) to "Hoi"), original { map })

        changed = original.change(
            Change(
                TestMarykModel { map.refToAny() } with "Hello"
            )
        )

        assertEquals(mapOf(LocalTime(12, 0) to "Hello", LocalTime(1, 2) to "Hello"), changed { map })
        assertEquals(mapOf(LocalTime(12, 0) to "Hi", LocalTime(1, 2) to "Hoi"), original { map })

        changed = original.change(
            Change(
                TestMarykModel { embeddedValues { value::ref } } with "bye"
            )
        )

        assertEquals("bye", changed { embeddedValues } / { value })
        assertEquals("hi", original { embeddedValues } / { value })

        changed = original.change(
            Change(
                TestMarykModel { embeddedValues { model { value::ref } } } with "goodbye"
            )
        )

        assertEquals("goodbye", changed { embeddedValues } / { model } / { value })
        assertEquals("bye", original { embeddedValues } / { model } / { value })

        assertFailsWith<RequestException> {
            // Cannot change non set sub values
            changed = original.change(
                Change(
                    TestMarykModel { embeddedValues { marykModel { string::ref } } } with "new"
                )
            )
        }
    }
}
