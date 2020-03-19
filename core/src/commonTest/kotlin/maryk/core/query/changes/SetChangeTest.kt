package maryk.core.query.changes

import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.extensions.toUnitLambda
import maryk.core.query.RequestContext
import maryk.core.values.div
import maryk.lib.time.Date
import maryk.lib.time.DateTime
import maryk.test.models.EmbeddedMarykModel
import maryk.test.models.TestMarykModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.expect

class SetChangeTest {
    private val setPropertyChange = SetChange(
        TestMarykModel { set::ref }.change(
            addValues = setOf(
                Date(2014, 4, 14),
                Date(2013, 3, 13)
            )
        )
    )

    private val context = RequestContext(
        mapOf(
            TestMarykModel.name toUnitLambda { TestMarykModel }
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
            dateTime = DateTime(2018, 7, 18),
            set = setOf(Date(2020, 2, 20), Date(2019, 12, 11)),
            embeddedValues = EmbeddedMarykModel(
                value = "test",
                marykModel = TestMarykModel(
                    string = "hi world",
                    int = 3,
                    uint = 67u,
                    double = 232523.3,
                    dateTime = DateTime(2020, 10, 18),
                    set = setOf(Date(2010, 2, 20), Date(2009, 12, 11))
                )
            )
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

        val deepChanged = original.change(
            SetChange(
                TestMarykModel { embeddedValues { marykModel { set::ref } } }.change(
                    addValues = setOf(
                        Date(1881, 12, 5), Date(1889, 5, 15)
                    )
                )
            )
        )

        assertEquals(
            setOf(Date(2010, 2, 20), Date(2009, 12, 11), Date(1881, 12, 5), Date(1889, 5, 15)),
            deepChanged { embeddedValues } / { marykModel } / { set }
        )
        assertEquals(
            setOf(Date(2010, 2, 20), Date(2009, 12, 11)),
            original { embeddedValues } / { marykModel } / { set }
        )
    }
}
