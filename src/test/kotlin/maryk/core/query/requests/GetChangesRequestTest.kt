package maryk.core.query.requests

import maryk.SubMarykObject
import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.core.properties.types.toUInt64
import maryk.core.query.properties.DataModelPropertyContext
import maryk.test.shouldBe
import kotlin.test.Test

class GetChangesRequestTest {
    private val key1 = SubMarykObject.key.getKey(SubMarykObject("test1"))
    private val key2 = SubMarykObject.key.getKey(SubMarykObject("test2"))

    private val getChangesRequest = GetChangesRequest(
            SubMarykObject,
            key1,
            key2,
            fromVersion = 1234L.toUInt64(),
            toVersion = 3456L.toUInt64()
    )

    private val context = DataModelPropertyContext(mapOf(
            SubMarykObject.name to SubMarykObject
    ))

    @Test
    fun testProtoBufConversion() {
        checkProtoBufConversion(this.getChangesRequest, GetChangesRequest, this.context, ::compareRequest)
    }

    @Test
    fun testJsonConversion() {
        checkJsonConversion(this.getChangesRequest, GetChangesRequest, this.context, ::compareRequest)
    }

    private fun compareRequest(converted: GetChangesRequest<*, *>) {
        converted.keys.contentDeepEquals(this.getChangesRequest.keys) shouldBe true
        converted.dataModel shouldBe this.getChangesRequest.dataModel
        converted.filterSoftDeleted shouldBe this.getChangesRequest.filterSoftDeleted
        converted.toVersion shouldBe this.getChangesRequest.toVersion
        converted.fromVersion shouldBe this.getChangesRequest.fromVersion
    }
}