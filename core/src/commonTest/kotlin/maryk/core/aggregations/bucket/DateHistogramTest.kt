package maryk.core.aggregations.bucket

import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.aggregations.Aggregations
import maryk.core.aggregations.metric.Sum
import maryk.core.properties.definitions.contextual.DataModelReference
import maryk.core.properties.types.DateUnit.Months
import maryk.core.query.RequestContext
import maryk.test.models.TestMarykModel
import kotlin.test.Test
import kotlin.test.expect

class DateHistogramTest {
    private val dateHistogram = DateHistogram(
        TestMarykModel { dateTime::ref },
        Months,
        Aggregations(
            "total" to Sum(
                TestMarykModel { double::ref }
            )
        )
    )

    private val context = RequestContext(
        mapOf(
            TestMarykModel.Meta.name to DataModelReference(TestMarykModel),
        ),
        dataModel = TestMarykModel
    )

    @Test
    fun convertToProtoBufAndBack() {
        checkProtoBufConversion(this.dateHistogram, DateHistogram, { this.context })
    }

    @Test
    fun convertToJSONAndBack() {
        expect(
            """
            {
              "of": "dateTime",
              "dateUnit": "Months",
              "aggregations": {
                "total": ["Sum", {
                  "of": "double"
                }]
              }
            }
            """.trimIndent()
        ) {
            checkJsonConversion(this.dateHistogram, DateHistogram, { this.context })
        }
    }

    @Test
    fun convertToYAMLAndBack() {
        expect(
            """
            of: dateTime
            dateUnit: Months
            aggregations:
              total: !Sum
                of: double

            """.trimIndent()
        ) {
            checkYamlConversion(this.dateHistogram, DateHistogram, { this.context })
        }
    }
}
