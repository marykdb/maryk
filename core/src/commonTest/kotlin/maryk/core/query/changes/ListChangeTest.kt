package maryk.core.query.changes

import kotlinx.datetime.LocalDateTime
import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.properties.definitions.contextual.DataModelReference
import maryk.core.query.RequestContext
import maryk.core.values.div
import maryk.test.models.EmbeddedMarykModel
import maryk.test.models.TestMarykModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.expect

class ListChangeTest {
    private val listPropertyChange = ListChange(
        TestMarykModel { listOfString::ref }.change(
            addValuesAtIndex = mapOf(2u to "a", 3u to "abc"),
            addValuesToEnd = listOf("four", "five"),
            deleteValues = listOf("three")
        )
    )

    private val context = RequestContext(
        mapOf(
            TestMarykModel.Meta.name to DataModelReference(TestMarykModel),
        ),
        dataModel = TestMarykModel
    )

    @Test
    fun convertToProtoBufAndBack() {
        checkProtoBufConversion(this.listPropertyChange, ListChange, { this.context })
    }

    @Test
    fun convertToJSONAndBack() {
        checkJsonConversion(this.listPropertyChange, ListChange, { this.context })
    }

    @Test
    fun convertToYAMLAndBack() {
        expect(
            """
            listOfString:
              addValuesToEnd: [four, five]
              addValuesAtIndex:
                2: a
                3: abc
              deleteValues: [three]

            """.trimIndent()
        ) {
            checkYamlConversion(this.listPropertyChange, ListChange, { this.context })
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
            list with listOf(3, 4, 5)
            embeddedValues with EmbeddedMarykModel.create {
                value with "test"
                marykModel with TestMarykModel.create {
                    string with "hi world"
                    int with 3
                    uint with 67u
                    double with 232523.3
                    dateTime with LocalDateTime(2020, 10, 18, 0, 0)
                    list with listOf(33, 44, 55)
                }
            }
        }

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

        val deepChanged = original.change(
            ListChange(
                TestMarykModel { embeddedValues { marykModel { list::ref } } }.change(
                    deleteValues = listOf(33),
                    addValuesAtIndex = mapOf(
                        1u to 9999
                    ),
                    addValuesToEnd = listOf(88)
                )
            )
        )

        assertEquals(listOf(44, 9999, 55, 88), deepChanged { embeddedValues } / { marykModel } / { list })
        assertEquals(listOf(33, 44, 55), original { embeddedValues } / { marykModel } / { list })
    }
}
