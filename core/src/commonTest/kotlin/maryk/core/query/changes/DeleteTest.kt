package maryk.core.query.changes

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.extensions.toUnitLambda
import maryk.core.properties.types.TypedValue
import maryk.core.query.RequestContext
import maryk.core.values.div
import maryk.lib.time.Time
import maryk.test.models.EmbeddedMarykModel
import maryk.test.models.SimpleMarykTypeEnum.S1
import maryk.test.models.TestMarykModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.expect

class DeleteTest {
    private val propertyDelete = Delete(
        TestMarykModel { string::ref }
    )

    private val propertyDeleteMultiple = Delete(
        TestMarykModel { string::ref },
        TestMarykModel { int::ref },
        TestMarykModel { dateTime::ref }
    )

    private val context = RequestContext(
        mapOf(
            TestMarykModel.name toUnitLambda { TestMarykModel }
        ),
        dataModel = TestMarykModel
    )

    @Test
    fun convertToProtoBufAndBack() {
        checkProtoBufConversion(this.propertyDelete, Delete, { this.context })
        checkProtoBufConversion(this.propertyDeleteMultiple, Delete, { this.context })
    }

    @Test
    fun convertToJSONAndBack() {
        checkJsonConversion(this.propertyDelete, Delete, { this.context })
        checkJsonConversion(this.propertyDeleteMultiple, Delete, { this.context })
    }

    @Test
    fun convertToYAMLAndBack() {
        expect(
            """
            string
            """.trimIndent()
        ) {
            checkYamlConversion(this.propertyDelete, Delete, { this.context })
        }

        expect(
            """
            - string
            - int
            - dateTime

            """.trimIndent()
        ) {
            checkYamlConversion(this.propertyDeleteMultiple, Delete, { this.context })
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
                Time(12, 0) to "Hi",
                Time(1, 2) to "Hoi"
            ),
            embeddedValues = EmbeddedMarykModel(
                value = "hi",
                model = EmbeddedMarykModel(
                    value = "bye"
                )
            )
        )

        var changed = original.change(listOf())

        assertEquals(original, changed)

        changed = original.change(
            Delete(TestMarykModel { int::ref })
        )

        assertNull(changed { int })
        assertEquals(5, original { int })

        changed = original.change(
            Delete(TestMarykModel { multi.refAtType(S1) })
        )

        assertNull(changed { multi }?.value)
        assertEquals("world", original { multi }?.value)

        changed = original.change(
            Delete(TestMarykModel { list::ref })
        )

        assertNull(changed { list })
        assertEquals(listOf(3, 4, 5), original { list })

        changed = original.change(
            Delete(TestMarykModel { list.refAt(0u) })
        )

        assertEquals(4, changed { list }?.getOrNull(0))
        assertEquals(3, original { list }?.getOrNull(0))

        changed = original.change(
            Delete(TestMarykModel { list.refToAny() })
        )

        assertEquals(emptyList(), changed { list })
        assertEquals(listOf(3, 4, 5), original { list })

        changed = original.change(
            Delete(TestMarykModel { map.refAt(Time(12, 0)) })
        )

        assertEquals(mapOf(Time(1, 2) to "Hoi"), changed { map })
        assertEquals(mapOf(Time(12, 0) to "Hi", Time(1, 2) to "Hoi"), original { map })

        changed = original.change(
            Delete(TestMarykModel { map.refToAny() })
        )

        assertEquals(emptyMap(), changed { map })
        assertEquals(mapOf(Time(12, 0) to "Hi", Time(1, 2) to "Hoi"), original { map })

        changed = original.change(
            Delete(TestMarykModel { embeddedValues { value::ref } })
        )

        assertNull(changed { embeddedValues } / { value })
        assertEquals("hi", original { embeddedValues } / { value })

        changed = original.change(
            Delete(TestMarykModel { embeddedValues { model { value::ref } } })
        )

        assertNull(changed { embeddedValues } / { model } / { value })
        assertEquals("bye", original { embeddedValues } / { model } / { value })

        changed = original.change(
            Delete(TestMarykModel { embeddedValues { marykModel { string::ref } } })
        )

        assertNull(changed { embeddedValues } / { marykModel } / { string })
        assertNull(original { embeddedValues } / { marykModel } / { string })
    }
}
