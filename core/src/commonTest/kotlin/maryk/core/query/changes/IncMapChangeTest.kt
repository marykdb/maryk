package maryk.core.query.changes

import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.extensions.toUnitLambda
import maryk.core.query.RequestContext
import maryk.test.models.CompleteMarykModel
import maryk.test.models.EmbeddedMarykModel
import kotlin.test.Test
import kotlin.test.expect

class IncMapChangeTest {
    private val incMapChange = IncMapChange(
        CompleteMarykModel { incMap::ref }.change(
            addValues = listOf(
                EmbeddedMarykModel("a"),
                EmbeddedMarykModel("b")
            )
        )
    )

    private val context = RequestContext(
        mapOf(
            CompleteMarykModel.name toUnitLambda { CompleteMarykModel }
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
}
