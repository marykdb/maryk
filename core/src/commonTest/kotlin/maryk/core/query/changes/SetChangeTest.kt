package maryk.core.query.changes

import kotlinx.datetime.LocalDate
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

class SetChangeTest {
    private val setPropertyChange = SetChange(
        TestMarykModel { set::ref }.change(
            addValues = setOf(
                LocalDate(2014, 4, 14),
                LocalDate(2013, 3, 13)
            )
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
        checkProtoBufConversion(this.setPropertyChange, SetChange, { this.context })
    }

    @Test
    fun convertToJSONAndBack() {
        checkJsonConversion(this.setPropertyChange, SetChange, { this.context })
    }

    @Test
    fun convertToYAMLAndBack() {
        expect(
            """
            set:
              addValues: [2014-04-14, 2013-03-13]

            """.trimIndent()
        ) {
            checkYamlConversion(this.setPropertyChange, SetChange, { this.context })
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
            set = setOf(LocalDate(2020, 2, 20), LocalDate(2019, 12, 11)),
            embeddedValues = EmbeddedMarykModel.create {
                value with "test"
                marykModel with TestMarykModel(
                    string = "hi world",
                    int = 3,
                    uint = 67u,
                    double = 232523.3,
                    dateTime = LocalDateTime(2020, 10, 18, 0, 0),
                    set = setOf(LocalDate(2010, 2, 20), LocalDate(2009, 12, 11))
                )
            }
        )

        val changed = original.change(
            SetChange(
                TestMarykModel { set::ref }.change(
                    addValues = setOf(
                        LocalDate(1981, 12, 5), LocalDate(1989, 5, 15)
                    )
                )
            )
        )

        assertEquals(
            setOf(LocalDate(2020, 2, 20), LocalDate(2019, 12, 11), LocalDate(1981, 12, 5), LocalDate(1989, 5, 15)),
            changed { set }
        )
        assertEquals(
            setOf(LocalDate(2020, 2, 20), LocalDate(2019, 12, 11)),
            original { set }
        )

        val deepChanged = original.change(
            SetChange(
                TestMarykModel { embeddedValues { marykModel { set::ref } } }.change(
                    addValues = setOf(
                        LocalDate(1881, 12, 5), LocalDate(1889, 5, 15)
                    )
                )
            )
        )

        assertEquals(
            setOf(LocalDate(2010, 2, 20), LocalDate(2009, 12, 11), LocalDate(1881, 12, 5), LocalDate(1889, 5, 15)),
            deepChanged { embeddedValues } / { marykModel } / { set }
        )
        assertEquals(
            setOf(LocalDate(2010, 2, 20), LocalDate(2009, 12, 11)),
            original { embeddedValues } / { marykModel } / { set }
        )
    }
}
