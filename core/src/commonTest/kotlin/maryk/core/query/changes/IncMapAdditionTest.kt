package maryk.core.query.changes

import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.extensions.toUnitLambda
import maryk.core.query.RequestContext
import maryk.test.models.CompleteMarykModel
import maryk.test.models.EmbeddedMarykModel
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
                EmbeddedMarykModel(value = "ho"),
                EmbeddedMarykModel(value = "ha")
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
            CompleteMarykModel.name toUnitLambda { CompleteMarykModel }
        ),
        dataModel = CompleteMarykModel
    )

    private val enrichedContext = RequestContext(
        dataModels = mapOf(
            CompleteMarykModel.name toUnitLambda { CompleteMarykModel }
        ),
        dataModel = CompleteMarykModel
    ).apply {
        collectIncMapChange(
            IncMapChange(
                CompleteMarykModel { incMap::ref }.change(
                    listOf(
                        EmbeddedMarykModel(value = "ho"),
                        EmbeddedMarykModel(value = "ha")
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
}
