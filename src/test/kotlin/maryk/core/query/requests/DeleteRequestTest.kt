package maryk.core.query.requests

import maryk.SimpleMarykObject
import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.core.query.DataModelPropertyContext
import kotlin.test.Test

class DeleteRequestTest {
    private val key1 = SimpleMarykObject.key.getKey(SimpleMarykObject("test1"))
    private val key2 = SimpleMarykObject.key.getKey(SimpleMarykObject("test2"))

    private val deleteRequest = DeleteRequest(
        SimpleMarykObject,
        key1,
        key2,
        hardDelete = true
    )

    private val context = DataModelPropertyContext(mapOf(
        SimpleMarykObject.name to SimpleMarykObject
    ))

    @Test
    fun testProtoBufConversion() {
        checkProtoBufConversion(this.deleteRequest, DeleteRequest, this.context)
    }

    @Test
    fun testJsonConversion() {
        checkJsonConversion(this.deleteRequest, DeleteRequest, this.context)
    }
}