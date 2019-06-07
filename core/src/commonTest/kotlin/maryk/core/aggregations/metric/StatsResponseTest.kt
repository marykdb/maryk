package maryk.core.aggregations.metric

import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.extensions.toUnitLambda
import maryk.core.query.RequestContext
import maryk.test.models.TestMarykModel
import kotlin.test.Test
import kotlin.test.expect

class StatsResponseTest {
    private val statsResponse = StatsResponse(
        reference = TestMarykModel { double::ref },
        valueCount = 123456uL,
        average = 456.0,
        min = 1.234,
        max = 53214.234,
        sum = 9.6554673E7
    )

    private val context = RequestContext(
        mapOf(
            TestMarykModel.name toUnitLambda { TestMarykModel }
        ),
        dataModel = TestMarykModel
    )

    @Test
    fun convertToProtoBufAndBack() {
        checkProtoBufConversion(this.statsResponse, StatsResponse, { this.context })
    }

    @Test
    fun convertToJSONAndBack() {
        expect(
            """
            {
              "of": "double",
              "valueCount": "123456",
              "average": "456.0",
              "min": "1.234",
              "max": "53214.234",
              "sum": "9.6554673E7"
            }
            """.trimIndent()
        ) {
            checkJsonConversion(this.statsResponse, StatsResponse, { this.context })
        }
    }

    @Test
    fun convertToYAMLAndBack() {
        expect(
            """
            of: double
            valueCount: 123456
            average: 456.0
            min: 1.234
            max: 53214.234
            sum: 9.6554673E7

            """.trimIndent()
        ) {
            checkYamlConversion(this.statsResponse, StatsResponse, { this.context })
        }
    }
}
