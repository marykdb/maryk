package maryk.core.aggregations.bucket

import kotlinx.datetime.LocalDateTime
import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.aggregations.AggregationsResponse
import maryk.core.aggregations.metric.SumResponse
import maryk.core.properties.definitions.contextual.DataModelReference
import maryk.core.query.RequestContext
import maryk.test.models.TestMarykModel
import kotlin.test.Test
import kotlin.test.expect

class DateHistogramResponseTest {
    private val dateHistogramResponse = DateHistogramResponse(
        reference = TestMarykModel { dateTime::ref },
        buckets = listOf(
            Bucket(
                LocalDateTime(2010, 1, 1, 0, 0),
                AggregationsResponse(
                    "total" to SumResponse(
                        TestMarykModel { int::ref },
                        123456789
                    )
                ),
                15uL
            ),
            Bucket(
                LocalDateTime(2011, 1, 1, 0, 0),
                AggregationsResponse(
                    "total" to SumResponse(
                        TestMarykModel { int::ref },
                        98373
                    )
                ),
                12uL
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
              }, {
                "key": "2011-01-01T00:00",
                "aggregations": {
                  "total": ["Sum", {
                    "of": "int",
                    "value": 98373
                  }]
                },
                "count": "12"
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
            - key: 2010-01-01T00:00
              aggregations:
                total: !Sum
                  of: int
                  value: 123456789
              count: 15
            - key: 2011-01-01T00:00
              aggregations:
                total: !Sum
                  of: int
                  value: 98373
              count: 12

            """.trimIndent()
        ) {
            checkYamlConversion(this.dateHistogramResponse, DateHistogramResponse, { this.context })
        }
    }
}
