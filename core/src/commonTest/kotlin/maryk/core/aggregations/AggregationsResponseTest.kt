package maryk.core.aggregations

import kotlinx.datetime.LocalDateTime
import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.aggregations.bucket.DateHistogramResponse
import maryk.core.aggregations.bucket.EnumValuesResponse
import maryk.core.aggregations.bucket.TypesResponse
import maryk.core.aggregations.metric.AverageResponse
import maryk.core.aggregations.metric.MaxResponse
import maryk.core.aggregations.metric.MinResponse
import maryk.core.aggregations.metric.SumResponse
import maryk.core.aggregations.metric.ValueCountResponse
import maryk.core.properties.definitions.contextual.DataModelReference
import maryk.core.query.RequestContext
import maryk.test.models.TestMarykModel
import kotlin.test.Test
import kotlin.test.expect

class AggregationsResponseTest {
    private val aggregationsResponse = AggregationsResponse(
        "total" to SumResponse(
            TestMarykModel { int::ref },
            3765476
        ),
        "mediocre" to AverageResponse(
            TestMarykModel { int::ref },
            3526,
            32uL
        ),
        "the least" to MinResponse(
            TestMarykModel { dateTime::ref },
            LocalDateTime(2019, 12, 1, 12, 3, 45)
        ),
        "the most" to MaxResponse(
            TestMarykModel { double::ref },
            3456.231
        ),
        "count" to ValueCountResponse(TestMarykModel { string::ref }, 1234uL),
        "by day" to DateHistogramResponse(
            TestMarykModel { dateTime::ref },
            listOf()
        ),
        "each enum" to EnumValuesResponse(
            TestMarykModel { enum::ref },
            listOf()
        ),
        "each type" to TypesResponse(
            TestMarykModel { multi.refToType() },
            listOf()
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
        checkProtoBufConversion(this.aggregationsResponse, AggregationsResponse, { this.context })
    }

    @Test
    fun convertToJSONAndBack() {
        expect(
            """
            {
              "total": ["Sum", {
                "of": "int",
                "value": 3765476
              }],
              "mediocre": ["Average", {
                "of": "int",
                "value": 3526,
                "valueCount": "32"
              }],
              "the least": ["Min", {
                "of": "dateTime",
                "value": "2019-12-01T12:03:45"
              }],
              "the most": ["Max", {
                "of": "double",
                "value": "3456.231"
              }],
              "count": ["ValueCount", {
                "of": "string",
                "value": "1234"
              }],
              "by day": ["DateHistogram", {
                "of": "dateTime",
                "buckets": []
              }],
              "each enum": ["EnumValues", {
                "of": "enum",
                "buckets": []
              }],
              "each type": ["Types", {
                "of": "multi.*",
                "buckets": []
              }]
            }
            """.trimIndent()
        ) {
            checkJsonConversion(this.aggregationsResponse, AggregationsResponse, { this.context })
        }
    }

    @Test
    fun convertToYAMLAndBack() {
        expect(
            """
            total: !Sum
              of: int
              value: 3765476
            mediocre: !Average
              of: int
              value: 3526
              valueCount: 32
            the least: !Min
              of: dateTime
              value: 2019-12-01T12:03:45
            the most: !Max
              of: double
              value: 3456.231
            count: !ValueCount
              of: string
              value: 1234
            by day: !DateHistogram
              of: dateTime
              buckets:
            each enum: !EnumValues
              of: enum
              buckets:
            each type: !Types
              of: multi.*
              buckets:

            """.trimIndent()
        ) {
            checkYamlConversion(this.aggregationsResponse, AggregationsResponse, { this.context })
        }
    }
}
