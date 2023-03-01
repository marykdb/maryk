package maryk.core.aggregations.metric

import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.extensions.toUnitLambda
import maryk.core.query.RequestContext
import maryk.test.models.TestMarykModel
import kotlin.test.Test
import kotlin.test.expect

class MinTest {
    private val min = Min(
        TestMarykModel { int::ref }
    )

    private val context = RequestContext(
        mapOf(
            TestMarykModel.Model.name toUnitLambda { TestMarykModel.Model }
        ),
        dataModel = TestMarykModel.Model
    )

    @Test
    fun convertToProtoBufAndBack() {
        checkProtoBufConversion(this.min, Min.Model, { this.context })
    }

    @Test
    fun convertToJSONAndBack() {
        expect(
            """
            {
              "of": "int"
            }
            """.trimIndent()
        ) {
            checkJsonConversion(this.min, Min.Model, { this.context })
        }
    }

    @Test
    fun convertToYAMLAndBack() {
        expect(
            """
            of: int

            """.trimIndent()
        ) {
            checkYamlConversion(this.min, Min.Model, { this.context })
        }
    }
}
