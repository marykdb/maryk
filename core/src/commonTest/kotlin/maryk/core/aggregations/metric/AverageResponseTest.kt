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

    private val avgResponseNull = AverageResponse(
        reference = TestMarykModel { int::ref },
        value = null,
        valueCount = 0uL
    )

    private val context = RequestContext(
        mapOf(
            TestMarykModel.Model.name toUnitLambda { TestMarykModel.Model }
        ),
        dataModel = TestMarykModel.Model
    )

    @Test
    fun convertToProtoBufAndBack() {
        checkProtoBufConversion(this.avgResponse, AverageResponse.Model, { this.context })
        checkProtoBufConversion(this.avgResponseNull, AverageResponse.Model, { this.context })
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
            checkJsonConversion(this.avgResponse, AverageResponse.Model, { this.context })
        }

        expect(
            """
            {
              "of": "int",
              "valueCount": "0"
            }
            """.trimIndent()
        ) {
            checkJsonConversion(this.avgResponseNull, AverageResponse.Model, { this.context })
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
            checkYamlConversion(this.avgResponse, AverageResponse.Model, { this.context })
        }

        expect(
            """
            of: int
            valueCount: 0

            """.trimIndent()
        ) {
            checkYamlConversion(this.avgResponseNull, AverageResponse.Model, { this.context })
        }
    }
}
