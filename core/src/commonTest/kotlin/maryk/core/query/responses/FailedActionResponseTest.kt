package maryk.core.query.responses

import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.extensions.toUnitLambda
import maryk.core.query.RequestContext
import maryk.core.query.responses.FailType.CONNECTION
import maryk.test.models.SimpleMarykModel
import kotlin.test.Test
import kotlin.test.expect

class FailedActionResponseTest {
    private val failedActionResponse = FailedActionResponse(
        "Something went wrong",
        CONNECTION
    )

    private val context = RequestContext(mapOf(
        SimpleMarykModel.Model.name toUnitLambda { SimpleMarykModel }
    ))

    @Test
    fun convertToProtoBufAndBack() {
        checkProtoBufConversion(this.failedActionResponse, FailedActionResponse, { this.context })
    }

    @Test
    fun convertToJSONAndBack() {
        checkJsonConversion(this.failedActionResponse, FailedActionResponse, { this.context })
    }

    @Test
    fun convertToYAMLAndBack() {
        expect(
            """
            message: Something went wrong
            failType: CONNECTION

            """.trimIndent()
        ) {
            checkYamlConversion(this.failedActionResponse, FailedActionResponse, { this.context })
        }
    }
}
