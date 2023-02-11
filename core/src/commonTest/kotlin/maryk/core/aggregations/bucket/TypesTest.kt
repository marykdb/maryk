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

class TypesTest {
    private val types = Types(
        TestMarykModel { multi.refToType() },
        Aggregations(
            "total" to Sum(
                TestMarykModel { double::ref }
            )
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
        checkProtoBufConversion(this.types, Types, { this.context })
    }

    @Test
    fun convertToJSONAndBack() {
        expect(
            """
            {
              "of": "multi.*",
              "aggregations": {
                "total": ["Sum", {
                  "of": "double"
                }]
              }
            }
            """.trimIndent()
        ) {
            checkJsonConversion(this.types, Types, { this.context })
        }
    }

    @Test
    fun convertToYAMLAndBack() {
        expect(
            """
            of: multi.*
            aggregations:
              total: !Sum
                of: double

            """.trimIndent()
        ) {
            checkYamlConversion(this.types, Types, { this.context })
        }
    }
}
