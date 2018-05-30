package maryk.core.query.responses

import maryk.SimpleMarykObject
import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.query.DataModelPropertyContext
import maryk.test.shouldBe
import kotlin.test.Test

class FailedActionResponseTest {
    private val failedActionResponse = FailedActionResponse(
        "Something went wrong",
        FailType.CONNECTION
    )

    private val context = DataModelPropertyContext(mapOf(
        SimpleMarykObject.name to SimpleMarykObject
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
