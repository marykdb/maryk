package maryk.core.aggregations.bucket

import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.aggregations.AggregationsResponse
import maryk.core.aggregations.metric.SumResponse
import maryk.core.extensions.toUnitLambda
import maryk.core.query.RequestContext
import maryk.lib.time.DateTime
import maryk.test.models.TestMarykModel
import kotlin.test.Test
import kotlin.test.expect

class DateHistogramResponseTest {
    private val dateHistogramResponse = DateHistogramResponse(
        reference = TestMarykModel { dateTime::ref },
        buckets = listOf(
            Bucket(
                DateTime(2010),
                AggregationsResponse(
                    mapOf(
                        "total" to SumResponse(
                            TestMarykModel { int::ref },
                            123456789
                        )
                    )
                ),
                15uL
            )
        )
    )

    private val context = RequestContext(
        mapOf(
            TestMarykModel.name toUnitLambda { TestMarykModel }
        ),
        dataModel = TestMarykModel
    )

    @Test
    fun convertToProtoBufAndBack() {
        checkProtoBufConversion(this.dateHistogramResponse, DateHistogramResponse, { this.context })
    }

    @Test
    fun convertToJSONAndBack() {
        expect(
            """
            {
              "of": "dateTime",
              "buckets": [{
                "key": "2010-01-01T00:00",
                "aggregations": {
                  "total": ["Sum", {
                    "of": "int",
                    "value": 123456789
                  }]
                },
                "count": "15"
              }]
            }
            """.trimIndent()
        ) {
            checkJsonConversion(this.dateHistogramResponse, DateHistogramResponse, { this.context })
        }
    }

    @Test
    fun convertToYAMLAndBack() {
        expect(
            """
            of: dateTime
            buckets:
            - key: '2010-01-01T00:00'
              aggregations:
                total: !Sum
                  of: int
                  value: 123456789
              count: 15

            """.trimIndent()
        ) {
            checkYamlConversion(this.dateHistogramResponse, DateHistogramResponse, { this.context })
        }
    }
}
