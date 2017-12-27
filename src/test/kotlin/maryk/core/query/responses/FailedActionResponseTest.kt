package maryk.core.query.responses

import maryk.SimpleMarykObject
import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.core.query.DataModelPropertyContext
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
    fun testProtoBufConversion() {
        checkProtoBufConversion(this.failedActionResponse, FailedActionResponse, this.context)
    }

    @Test
    fun testJsonConversion() {
        checkJsonConversion(this.failedActionResponse, FailedActionResponse, this.context)
    }
}