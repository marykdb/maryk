package maryk.core.query.requests

import maryk.SubMarykObject
import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.core.properties.types.toUInt64
import maryk.core.query.properties.DataModelPropertyContext
import maryk.test.shouldBe
import kotlin.test.Test

class GetRequestTest {
    private val key1 = SubMarykObject.key.getKey(SubMarykObject("test1"))
    private val key2 = SubMarykObject.key.getKey(SubMarykObject("test2"))

    private val getRequest = GetRequest(
            SubMarykObject,
            key1,
            key2,
            toVersion = 333L.toUInt64(),
            filterSoftDeleted = true
    )

    private val context = DataModelPropertyContext(mapOf(
            SubMarykObject.name to SubMarykObject
    ))

    @Test
    fun testProtoBufConversion() {
        checkProtoBufConversion(this.getRequest, GetRequest, this.context, ::compareRequest)
    }

    @Test
    fun testJsonConversion() {
        checkJsonConversion(this.getRequest, GetRequest, this.context, ::compareRequest)
    }

    private fun compareRequest(converted: GetRequest<*, *>) {
        converted.keys.contentDeepEquals(this.getRequest.keys) shouldBe true
        converted.dataModel shouldBe this.getRequest.dataModel
        converted.filterSoftDeleted shouldBe this.getRequest.filterSoftDeleted
        converted.toVersion shouldBe this.getRequest.toVersion
    }
}