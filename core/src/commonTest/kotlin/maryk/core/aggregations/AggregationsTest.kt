package maryk.core.aggregations

import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.aggregations.bucket.DateHistogram
import maryk.core.aggregations.bucket.EnumValues
import maryk.core.aggregations.bucket.Types
import maryk.core.aggregations.metric.Average
import maryk.core.aggregations.metric.Max
import maryk.core.aggregations.metric.Min
import maryk.core.aggregations.metric.Sum
import maryk.core.aggregations.metric.ValueCount
import maryk.core.extensions.toUnitLambda
import maryk.core.properties.types.DateUnit
import maryk.core.query.RequestContext
import maryk.test.models.TestMarykModel
import kotlin.test.Test
import kotlin.test.expect

class AggregationsTest {
    private val aggregations = Aggregations(
        "total" to Sum(
            TestMarykModel { int::ref }
        ),
        "mediocre" to Average(
            TestMarykModel { int::ref }
        ),
        "the least" to Min(
            TestMarykModel { int::ref }
        ),
        "the most" to Max(
            TestMarykModel { int::ref }
        ),
        "count" to ValueCount(
            TestMarykModel { int::ref }
        ),
        "by day" to DateHistogram(
            TestMarykModel { dateTime::ref },
            DateUnit.Days
        ),
        "each enum" to EnumValues(
            TestMarykModel { enum::ref }
        ),
        "each type" to Types(
            TestMarykModel { multi.refToType() }
        )
    )

    private val context = RequestContext(
        mapOf(
            TestMarykModel.Model.name toUnitLambda { TestMarykModel.Model }
        ),
        dataModel = TestMarykModel.Model
    )

    @Test
    fun convertToProtoBufAndBack() {
        checkProtoBufConversion(this.aggregations, Aggregations.Model, { this.context })
    }

    @Test
    fun convertToJSONAndBack() {
        expect(
            """
            {
              "total": ["Sum", {
                "of": "int"
              }],
              "mediocre": ["Average", {
                "of": "int"
              }],
              "the least": ["Min", {
                "of": "int"
              }],
              "the most": ["Max", {
                "of": "int"
              }],
              "count": ["ValueCount", {
                "of": "int"
              }],
              "by day": ["DateHistogram", {
                "of": "dateTime",
                "dateUnit": "Days"
              }],
              "each enum": ["EnumValues", {
                "of": "enum"
              }],
              "each type": ["Types", {
                "of": "multi.*"
              }]
            }
            """.trimIndent()
        ) {
            checkJsonConversion(this.aggregations, Aggregations.Model, { this.context })
        }
    }

    @Test
    fun convertToYAMLAndBack() {
        expect(
            """
            total: !Sum
              of: int
            mediocre: !Average
              of: int
            the least: !Min
              of: int
            the most: !Max
              of: int
            count: !ValueCount
              of: int
            by day: !DateHistogram
              of: dateTime
              dateUnit: Days
            each enum: !EnumValues
              of: enum
            each type: !Types
              of: multi.*

            """.trimIndent()
        ) {
            checkYamlConversion(this.aggregations, Aggregations.Model, { this.context })
        }
    }
}
