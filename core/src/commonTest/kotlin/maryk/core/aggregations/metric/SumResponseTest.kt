package maryk.core.aggregations.metric

import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.extensions.toUnitLambda
import maryk.core.query.RequestContext
import maryk.test.models.TestMarykModel
import kotlin.test.Test
import kotlin.test.expect

class SumResponseTest {
    private val sumResponse = SumResponse(
        reference = TestMarykModel { double::ref },
        value = 1.234
    )

    private val context = RequestContext(
        mapOf(
            TestMarykModel.Model.name toUnitLambda { TestMarykModel.Model }
        ),
        dataModel = TestMarykModel.Model
    )

    @Test
    fun convertToProtoBufAndBack() {
        checkProtoBufConversion(this.sumResponse, SumResponse, { this.context })
    }

    @Test
    fun convertToJSONAndBack() {
        expect(
            """
            {
              "of": "double",
              "value": "1.234"
            }
            """.trimIndent()
        ) {
            checkJsonConversion(this.sumResponse, SumResponse.Model, { this.context })
        }
    }

    @Test
    fun convertToYAMLAndBack() {
        expect(
            """
            of: double
            value: 1.234

            """.trimIndent()
        ) {
            checkYamlConversion(this.sumResponse, SumResponse.Model, { this.context })
        }
    }
}
