package maryk.core.aggregations.bucket

import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.aggregations.AggregationsResponse
import maryk.core.aggregations.metric.AverageResponse
import maryk.core.aggregations.metric.SumResponse
import maryk.core.extensions.toUnitLambda
import maryk.core.query.RequestContext
import maryk.test.models.Option.V1
import maryk.test.models.Option.V2
import maryk.test.models.TestMarykModel
import kotlin.test.Test
import kotlin.test.expect

class EnumValuesResponseTest {
    private val enumValuesResponse = EnumValuesResponse(
        reference = TestMarykModel { enum::ref },
        buckets = listOf(
            Bucket(
                V1,
                AggregationsResponse(
                    mapOf(
                        "total" to SumResponse(
                            TestMarykModel { int::ref },
                            123456789
                        ),
                        "avg" to AverageResponse(
                            TestMarykModel { int::ref },
                            43728,
                            32uL
                        )
                    )
                ),
                15uL
            ),
            Bucket(
                V2,
                AggregationsResponse(
                    mapOf(
                        "total" to SumResponse(
                            TestMarykModel { int::ref },
                            1
                        ),
                        "avg" to AverageResponse(
                            TestMarykModel { int::ref },
                            5322,
                            2uL
                        )
                    )
                ),
                2uL
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
        checkProtoBufConversion(this.enumValuesResponse, EnumValuesResponse, { this.context })
    }

    @Test
    fun convertToJSONAndBack() {
        expect(
            """
            {
              "of": "enum",
              "buckets": [{
                "key": "V1(1)",
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
                "key": "V2(2)",
                "aggregations": {
                  "total": ["Sum", {
                    "of": "int",
                    "value": 1
                  }],
                  "avg": ["Average", {
                    "of": "int",
                    "value": 5322,
                    "valueCount": "2"
                  }]
                },
                "count": "2"
              }]
            }
            """.trimIndent()
        ) {
            checkJsonConversion(this.enumValuesResponse, EnumValuesResponse, { this.context })
        }
    }

    @Test
    fun convertToYAMLAndBack() {
        expect(
            """
            of: enum
            buckets:
            - key: V1(1)
              aggregations:
                total: !Sum
                  of: int
                  value: 123456789
                avg: !Average
                  of: int
                  value: 43728
                  valueCount: 32
              count: 15
            - key: V2(2)
              aggregations:
                total: !Sum
                  of: int
                  value: 1
                avg: !Average
                  of: int
                  value: 5322
                  valueCount: 2
              count: 2

            """.trimIndent()
        ) {
            checkYamlConversion(this.enumValuesResponse, EnumValuesResponse, { this.context })
        }
    }
}
