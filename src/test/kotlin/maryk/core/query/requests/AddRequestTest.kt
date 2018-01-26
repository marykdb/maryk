package maryk.core.query.requests

import maryk.SimpleMarykObject
import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.core.query.DataModelPropertyContext
import kotlin.test.Test

class AddRequestTest {
    private val addRequest = AddRequest(
        SimpleMarykObject,
        SimpleMarykObject(value = "haha1"),
        SimpleMarykObject(value = "haha2")
    )

    private val context = DataModelPropertyContext(mapOf(
        SimpleMarykObject.name to SimpleMarykObject
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