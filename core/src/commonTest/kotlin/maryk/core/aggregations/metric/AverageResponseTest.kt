package maryk.core.aggregations.metric

import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.extensions.toUnitLambda
import maryk.core.query.RequestContext
import maryk.test.models.TestMarykModel
import kotlin.test.Test
import kotlin.test.expect

class AverageResponseTest {
    private val avgResponse = AverageResponse(
        reference = TestMarykModel { int::ref },
        value = 1234,
        valueCount = 32uL
    )

    private val context = RequestContext(
        mapOf(
            TestMarykModel.name toUnitLambda { TestMarykModel }
        ),
        dataModel = TestMarykModel
    )

    @Test
    fun convertToProtoBufAndBack() {
        checkProtoBufConversion(this.avgResponse, AverageResponse, { this.context })
    }

    @Test
    fun convertToJSONAndBack() {
        expect(
            """
            {
              "of": "int",
              "value": 1234,
              "valueCount": "32"
            }
            """.trimIndent()
        ) {
            checkJsonConversion(this.avgResponse, AverageResponse, { this.context })
        }
    }

    @Test
    fun convertToYAMLAndBack() {
        expect(
            """
            of: int
            value: 1234
            valueCount: 32

            """.trimIndent()
        ) {
            checkYamlConversion(this.avgResponse, AverageResponse, { this.context })
        }
    }
}
