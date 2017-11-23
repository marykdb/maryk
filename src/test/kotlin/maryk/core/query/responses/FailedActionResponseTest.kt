package maryk.core.query.responses

import maryk.SubMarykObject
import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.core.query.DataModelPropertyContext
import kotlin.test.Test

class FailedActionResponseTest {
    private val value = SubMarykObject(value = "haha1")

    private val key = SubMarykObject.key.getKey(this.value)

    private val failedActionResponse = FailedActionResponse(
            "Something went wrong",
            FailType.CONNECTION
    )

    private val context = DataModelPropertyContext(mapOf(
            SubMarykObject.name to SubMarykObject
    ))

    @Test
    fun testProtoBufConversion() {
        checkProtoBufConversion(this.failedActionResponse, FailedActionResponse, this.context)
    }

    @Test
    fun testJsonConversion() {
        checkJsonConversion(this.failedActionResponse, FailedActionResponse, this.context)
    }
}