package maryk.core.aggregations.metric

import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.properties.definitions.contextual.DataModelReference
import maryk.core.query.RequestContext
import maryk.test.models.TestMarykModel
import kotlin.test.Test
import kotlin.test.expect

class MaxResponseTest {
    private val maxResponse = MaxResponse(
        reference = TestMarykModel { int::ref },
        value = 1234
    )

    private val maxResponseNull = MaxResponse(
        reference = TestMarykModel { int::ref },
        value = null
    )

    private val context = RequestContext(
        mapOf(
            TestMarykModel.Meta.name to DataModelReference(TestMarykModel),
        ),
        dataModel = TestMarykModel
    )

    @Test
    fun convertToProtoBufAndBack() {
        checkProtoBufConversion(this.maxResponse, MaxResponse, { this.context })
        checkProtoBufConversion(this.maxResponseNull, MaxResponse, { this.context })
    }

    @Test
    fun convertToJSONAndBack() {
        expect(
            """
            {
              "of": "int",
              "value": 1234
            }
            """.trimIndent()
        ) {
            checkJsonConversion(this.maxResponse, MaxResponse, { this.context })
        }

        expect(
            """
            {
              "of": "int"
            }
            """.trimIndent()
        ) {
            checkJsonConversion(this.maxResponseNull, MaxResponse, { this.context })
        }
    }

    @Test
    fun convertToYAMLAndBack() {
        expect(
            """
            of: int

            """.trimIndent()
        ) {
            checkYamlConversion(this.maxResponseNull, MaxResponse, { this.context })
        }
    }
}
