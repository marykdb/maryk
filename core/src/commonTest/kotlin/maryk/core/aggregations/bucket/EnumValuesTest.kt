package maryk.core.aggregations.bucket

import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.aggregations.Aggregations
import maryk.core.aggregations.metric.Sum
import maryk.core.extensions.toUnitLambda
import maryk.core.query.RequestContext
import maryk.test.models.TestMarykModel
import kotlin.test.Test
import kotlin.test.expect

class EnumValuesTest {
    private val enumValues = EnumValues(
        TestMarykModel { enum::ref },
        Aggregations(
            "total" to Sum(
                TestMarykModel { double::ref }
            )
        )
    )

    private val context = RequestContext(
        mapOf(
            TestMarykModel.Model.name toUnitLambda { TestMarykModel }
        ),
        dataModel = TestMarykModel
    )

    @Test
    fun convertToProtoBufAndBack() {
        checkProtoBufConversion(this.enumValues, EnumValues, { this.context })
    }

    @Test
    fun convertToJSONAndBack() {
        expect(
            """
            {
              "of": "enum",
              "aggregations": {
                "total": ["Sum", {
                  "of": "double"
                }]
              }
            }
            """.trimIndent()
        ) {
            checkJsonConversion(this.enumValues, EnumValues, { this.context })
        }
    }

    @Test
    fun convertToYAMLAndBack() {
        expect(
            """
            of: enum
            aggregations:
              total: !Sum
                of: double

            """.trimIndent()
        ) {
            checkYamlConversion(this.enumValues, EnumValues, { this.context })
        }
    }
}
