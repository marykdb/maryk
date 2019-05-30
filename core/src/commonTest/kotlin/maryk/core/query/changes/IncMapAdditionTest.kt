package maryk.core.query.changes

import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.extensions.toUnitLambda
import maryk.core.query.RequestContext
import maryk.test.models.CompleteMarykModel
import kotlin.test.Test
import kotlin.test.expect

class IncMapAdditionTest {
    private val indMapAddition = IncMapAddition(
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

    @Test
    fun convertToProtoBufAndBack() {
        checkProtoBufConversion(this.indMapAddition, IncMapAddition, { this.context })
    }

    @Test
    fun convertToJSONAndBack() {
        checkJsonConversion(this.indMapAddition, IncMapAddition, { this.context })
    }

    @Test
    fun convertToYAMLAndBack() {
        expect(
            """
            incMap:
              addedKeys: [22, 23]

            """.trimIndent()
        ) {
            checkYamlConversion(this.indMapAddition, IncMapAddition, { this.context })
        }
    }
}
