package maryk.core.query.changes

import kotlinx.datetime.LocalDateTime
import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.extensions.toUnitLambda
import maryk.core.query.RequestContext
import maryk.core.values.div
import maryk.test.models.CompleteMarykModel
import maryk.test.models.EmbeddedMarykModel
import maryk.test.models.TestMarykModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.expect

class IncMapAdditionTest {
    private val incMapAddition = IncMapAddition(
        IncMapKeyAdditions(
            CompleteMarykModel { incMap::ref },
            listOf(
                22u,
                23u
            ),
            listOf(
                EmbeddedMarykModel.run { create(value with "ho") },
                EmbeddedMarykModel.run { create(value with "ha") },
            )
        )
    )

    private val incMapLessAddition = IncMapAddition(
        IncMapKeyAdditions(
            CompleteMarykModel { incMap::ref },
            listOf(
                22u,
                23u
            )
        )
    )

    private val context = RequestContext(
        mapOf(
            CompleteMarykModel.Model.name toUnitLambda { CompleteMarykModel }
        ),
        dataModel = CompleteMarykModel
    )

    private val enrichedContext = RequestContext(
        dataModels = mapOf(
            CompleteMarykModel.Model.name toUnitLambda { CompleteMarykModel }
        ),
        dataModel = CompleteMarykModel
    ).apply {
        collectIncMapChange(
            IncMapChange(
                CompleteMarykModel { incMap::ref }.change(
                    listOf(
                        EmbeddedMarykModel.run { create(value with "ho") },
                        EmbeddedMarykModel.run { create(value with "ha") },
                    )
                )
            )
        )
    }

    private val conversionChecker = { converted: IncMapAddition, _: IncMapAddition ->
        assertEquals(incMapLessAddition, converted)
    }

    @Test
    fun convertToProtoBufAndBackWithoutEnrichedContext() {
        checkProtoBufConversion(this.incMapAddition, IncMapAddition, { this.context }, conversionChecker)
    }

    @Test
    fun convertToJSONAndBackWithoutEnrichedContext() {
        checkJsonConversion(this.incMapAddition, IncMapAddition, { this.context }, conversionChecker)
    }

    @Test
    fun convertToYAMLAndBackWithoutEnrichedContext() {
        expect(
            """
            incMap:
              addedKeys: [22, 23]

            """.trimIndent()
        ) {
            checkYamlConversion(this.incMapAddition, IncMapAddition, { this.context }, conversionChecker)
        }
    }

    @Test
    fun convertToProtoBufAndBackWithEnrichedContext() {
        checkProtoBufConversion(this.incMapAddition, IncMapAddition, { enrichedContext })
    }

    @Test
    fun convertToJSONAndBackWithEnrichedContext() {
        checkJsonConversion(this.incMapAddition, IncMapAddition, { enrichedContext })
    }

    @Test
    fun convertToYAMLAndBackWithEnrichedContext() {
        expect(
            """
            incMap:
              addedKeys: [22, 23]

            """.trimIndent()
        ) {
            checkYamlConversion(this.incMapAddition, IncMapAddition, { enrichedContext })
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
            incMap = mapOf(
                1u to "one",
                2u to "two",
                3u to "three"
            ),
            embeddedValues = EmbeddedMarykModel.run { create(
                value with "test",
                marykModel with TestMarykModel(
                    string = "hi world",
                    int = 3,
                    uint = 67u,
                    double = 232523.3,
                    dateTime = LocalDateTime(2020, 10, 18, 0, 0),
                    incMap = mapOf(
                        11u to "eleven",
                        12u to "twelve",
                        13u to "thirteen"
                    )
                )
            ) }
        )

        val changed = original.change(
            IncMapAddition(
                IncMapKeyAdditions(
                    TestMarykModel { incMap::ref },
                    addedKeys = listOf(4u, 5u),
                    addedValues = listOf(
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
            IncMapAddition(
                IncMapKeyAdditions(
                    TestMarykModel { embeddedValues { marykModel { incMap::ref } } },
                    addedKeys = listOf(14u, 15u),
                    addedValues = listOf(
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
}
