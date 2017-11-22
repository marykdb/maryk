package maryk.core.query.requests

import maryk.SubMarykObject
import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.core.query.properties.DataModelPropertyContext
import maryk.test.shouldBe
import kotlin.test.Test

class DeleteRequestTest {
    private val key1 = SubMarykObject.key.getKey(SubMarykObject("test1"))
    private val key2 = SubMarykObject.key.getKey(SubMarykObject("test2"))

    private val deleteRequest = DeleteRequest(
            SubMarykObject,
            key1,
            key2,
            hardDelete = true
    )

    private val context = DataModelPropertyContext(mapOf(
            SubMarykObject.name to SubMarykObject
    ))

    @Test
    fun testProtoBufConversion() {
        checkProtoBufConversion(this.deleteRequest, DeleteRequest, this.context, ::compareRequest)
    }

    @Test
    fun testJsonConversion() {
        checkJsonConversion(this.deleteRequest, DeleteRequest, this.context, ::compareRequest)
    }

    private fun compareRequest(converted: DeleteRequest<*, *>) {
        converted.objectsToDelete.contentDeepEquals(this.deleteRequest.objectsToDelete) shouldBe true
        converted.dataModel shouldBe this.deleteRequest.dataModel
        converted.hardDelete shouldBe this.deleteRequest.hardDelete
    }
}