package maryk.core.query.changes

import kotlinx.datetime.LocalDateTime
import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.properties.definitions.contextual.DataModelReference
import maryk.core.query.RequestContext
import maryk.core.values.div
import maryk.test.models.CompleteMarykModel
import maryk.test.models.EmbeddedMarykModel
import maryk.test.models.TestMarykModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.expect

class IncMapChangeTest {
    private val incMapChange = IncMapChange(
        CompleteMarykModel { incMap::ref }.change(
            addValues = listOf(
                EmbeddedMarykModel.create { value with "a" },
                EmbeddedMarykModel.create { value with "b" },
            )
        )
    )

    private val context = RequestContext(
        mapOf(
            CompleteMarykModel.Meta.name to DataModelReference(CompleteMarykModel),
        ),
        dataModel = CompleteMarykModel
    )

    @Test
    fun convertToProtoBufAndBack() {
        checkProtoBufConversion(this.incMapChange, IncMapChange, { this.context })
    }

    @Test
    fun convertToJSONAndBack() {
        checkJsonConversion(this.incMapChange, IncMapChange, { this.context })
    }

    @Test
    fun convertToYAMLAndBack() {
        expect(
            """
            incMap:
              addValues:
              - value: a
              - value: b

            """.trimIndent()
        ) {
            checkYamlConversion(this.incMapChange, IncMapChange, { this.context })
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
            incMap with mapOf(
                1u to "one",
                2u to "two",
                3u to "three"
            )
            embeddedValues with {
                value with "test"
                marykModel with {
                    string with "hi world"
                    int with 3
                    uint with 67u
                    double with 232523.3
                    dateTime with LocalDateTime(2020, 10, 18, 0, 0)
                    incMap with mapOf(
                        11u to "eleven",
                        12u to "twelve",
                        13u to "thirteen"
                    )
                }
            }
        }

        val changed = original.change(
            IncMapChange(
                TestMarykModel { incMap::ref }.change(
                    addValues = listOf(
                        "four",
                        "five"
                    )
                )
            )
        )

        assertEquals(mapOf(
            1u to "one",
            2u to "two",
            3u to "three",
            4u to "four",
            5u to "five"
        ), changed { incMap })

        assertEquals(mapOf(
            1u to "one",
            2u to "two",
            3u to "three"
        ), original { incMap })

        val deepChanged = original.change(
            IncMapChange(
                TestMarykModel { embeddedValues { marykModel { incMap::ref } } }.change(
                    addValues = listOf(
                        "fourteen",
                        "fifteen"
                    )
                )
            )
        )

        assertEquals(mapOf(
            11u to "eleven",
            12u to "twelve",
            13u to "thirteen",
            14u to "fourteen",
            15u to "fifteen"
        ), deepChanged { embeddedValues } / { marykModel } / { incMap })
        assertEquals(mapOf(
            11u to "eleven",
            12u to "twelve",
            13u to "thirteen"
        ), original { embeddedValues } / { marykModel } / { incMap })
    }

    @Test
    fun changeValuesOnEmptyIncMapStartsAtOne() {
        val original = TestMarykModel.create {
            incMap with emptyMap()
        }

        val changed = original.change(
            IncMapChange(
                TestMarykModel { incMap::ref }.change(
                    addValues = listOf("first")
                )
            )
        )

        assertEquals(mapOf(1u to "first"), changed { incMap })
        assertEquals(emptyMap(), original { incMap })
    }
}
