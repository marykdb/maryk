package maryk.core.query.requests

import maryk.SubMarykObject
import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.core.query.DataModelPropertyContext
import kotlin.test.Test

class AddRequestTest {
    private val addRequest = AddRequest(
            SubMarykObject,
            SubMarykObject(value = "haha1"),
            SubMarykObject(value = "haha2")
    )

    private val context = DataModelPropertyContext(mapOf(
            SubMarykObject.name to SubMarykObject
    ))

    @Test
    fun testProtoBufConversion() {
        checkProtoBufConversion(this.addRequest, AddRequest, this.context)
    }

    @Test
    fun testJsonConversion() {
        checkJsonConversion(this.addRequest, AddRequest, this.context)
    }
}