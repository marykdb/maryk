package maryk.core.aggregations.metric

import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.properties.definitions.contextual.DataModelReference
import maryk.core.query.RequestContext
import maryk.test.models.TestMarykModel
import kotlin.test.Test
import kotlin.test.expect

class StatsResponseTest {
    private val statsResponse = StatsResponse(
        reference = TestMarykModel { int::ref },
        valueCount = 123456uL,
        average = 25435,
        min = 1234,
        max = 83672,
        sum = 12651245
    )

    private val context = RequestContext(
        mapOf(
            TestMarykModel.Meta.name to DataModelReference(TestMarykModel),
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
              "of": "int",
              "valueCount": "123456",
              "average": 25435,
              "min": 1234,
              "max": 83672,
              "sum": 12651245
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
            of: int
            valueCount: 123456
            average: 25435
            min: 1234
            max: 83672
            sum: 12651245

            """.trimIndent()
        ) {
            checkYamlConversion(this.statsResponse, StatsResponse, { this.context })
        }
    }
}
