package maryk.core.aggregations.bucket

import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.aggregations.AggregationsResponse
import maryk.core.aggregations.metric.AverageResponse
import maryk.core.aggregations.metric.SumResponse
import maryk.core.extensions.toUnitLambda
import maryk.core.query.RequestContext
import maryk.test.models.SimpleMarykTypeEnum.S1
import maryk.test.models.SimpleMarykTypeEnum.S2
import maryk.test.models.TestMarykModel
import kotlin.test.Test
import kotlin.test.expect

class TypesResponseTest {
    private val typesResponse = TypesResponse(
        reference = TestMarykModel { multi.refToType() },
        buckets = listOf(
            Bucket(
                S1,
                AggregationsResponse(
                    "total" to SumResponse(
                        TestMarykModel { int::ref },
                        123456789
                    ),
                    "avg" to AverageResponse(
                        TestMarykModel { int::ref },
                        43728,
                        32uL
                    )
                ),
                15uL
            ),
            Bucket(
                S2,
                AggregationsResponse(
                    "total" to SumResponse(
                        TestMarykModel { int::ref },
                        213683
                    ),
                    "avg" to AverageResponse(
                        TestMarykModel { int::ref },
                        8823234,
                        64uL
                    )
                ),
                23uL
            )
        )
    )

    private val context = RequestContext(
        mapOf(
            TestMarykModel.Model.name toUnitLambda { TestMarykModel.Model }
        ),
        dataModel = TestMarykModel
    )

    @Test
    fun convertToProtoBufAndBack() {
        checkProtoBufConversion(this.typesResponse, TypesResponse, { this.context })
    }

    @Test
    fun convertToJSONAndBack() {
        expect(
            """
            {
              "of": "multi.*",
              "buckets": [{
                "key": "S1(1)",
                "aggregations": {
                  "total": ["Sum", {
                    "of": "int",
                    "value": 123456789
                  }],
                  "avg": ["Average", {
                    "of": "int",
                    "value": 43728,
                    "valueCount": "32"
                  }]
                },
                "count": "15"
              }, {
                "key": "S2(2)",
                "aggregations": {
                  "total": ["Sum", {
                    "of": "int",
                    "value": 213683
                  }],
                  "avg": ["Average", {
                    "of": "int",
                    "value": 8823234,
                    "valueCount": "64"
                  }]
                },
                "count": "23"
              }]
            }
            """.trimIndent()
        ) {
            checkJsonConversion(this.typesResponse, TypesResponse, { this.context })
        }
    }

    @Test
    fun convertToYAMLAndBack() {
        expect(
            """
            of: multi.*
            buckets:
            - key: S1(1)
              aggregations:
                total: !Sum
                  of: int
                  value: 123456789
                avg: !Average
                  of: int
                  value: 43728
                  valueCount: 32
              count: 15
            - key: S2(2)
              aggregations:
                total: !Sum
                  of: int
                  value: 213683
                avg: !Average
                  of: int
                  value: 8823234
                  valueCount: 64
              count: 23

            """.trimIndent()
        ) {
            checkYamlConversion(this.typesResponse, TypesResponse, { this.context })
        }
    }
}
