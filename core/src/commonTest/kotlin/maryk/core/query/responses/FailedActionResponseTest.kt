package maryk.core.query.responses

import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.extensions.toUnitLambda
import maryk.core.query.RequestContext
import maryk.test.models.SimpleMarykModel
import maryk.test.shouldBe
import kotlin.test.Test

class FailedActionResponseTest {
    private val failedActionResponse = FailedActionResponse(
        "Something went wrong",
        FailType.CONNECTION
    )

    private val context = RequestContext(mapOf(
        SimpleMarykModel.name toUnitLambda { SimpleMarykModel }
    ))

    @Test
    fun convert_to_ProtoBuf_and_back() {
        checkProtoBufConversion(this.failedActionResponse, FailedActionResponse, { this.context })
    }

    @Test
    fun convert_to_JSON_and_back() {
        checkJsonConversion(this.failedActionResponse, FailedActionResponse, { this.context })
    }

    @Test
    fun convert_to_YAML_and_back() {
        checkYamlConversion(this.failedActionResponse, FailedActionResponse, { this.context }) shouldBe """
        message: Something went wrong
        failType: CONNECTION

        """.trimIndent()
    }
}
