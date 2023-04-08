package maryk.core.aggregations.metric

import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.extensions.toUnitLambda
import maryk.core.query.RequestContext
import maryk.test.models.TestMarykModel
import kotlin.test.Test
import kotlin.test.expect

class ValueCountResponseTest {
    private val valueCountResponse = ValueCountResponse(
        reference = TestMarykModel { string::ref },
        value = 1234uL
    )

    private val context = RequestContext(
        mapOf(
            TestMarykModel.Meta.name toUnitLambda { TestMarykModel }
        ),
        dataModel = TestMarykModel
    )

    @Test
    fun convertToProtoBufAndBack() {
        checkProtoBufConversion(this.valueCountResponse, ValueCountResponse, { this.context })
    }

    @Test
    fun convertToJSONAndBack() {
        expect(
            """
            {
              "of": "string",
              "value": "1234"
            }
            """.trimIndent()
        ) {
            checkJsonConversion(this.valueCountResponse, ValueCountResponse, { this.context })
        }
    }

    @Test
    fun convertToYAMLAndBack() {
        expect(
            """
            of: string
            value: 1234

            """.trimIndent()
        ) {
            checkYamlConversion(this.valueCountResponse, ValueCountResponse, { this.context })
        }
    }
}
