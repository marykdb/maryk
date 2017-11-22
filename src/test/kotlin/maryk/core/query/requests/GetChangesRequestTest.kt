package maryk.core.query.requests

import maryk.SubMarykObject
import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.core.properties.types.toUInt64
import maryk.core.query.DataModelPropertyContext
import maryk.core.query.Order
import maryk.core.query.filters.Exists
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

    private val getChangesMaxRequest = GetChangesRequest(
            SubMarykObject,
            key1,
            key2,
            filter = Exists(SubMarykObject.Properties.value.getRef()),
            order = Order(SubMarykObject.Properties.value.getRef()),
            fromVersion = 1234L.toUInt64(),
            toVersion = 3456L.toUInt64(),
            filterSoftDeleted = true
    )

    private val context = DataModelPropertyContext(mapOf(
            SubMarykObject.name to SubMarykObject
    ))

    @Test
    fun testProtoBufConversion() {
        checkProtoBufConversion(this.getChangesRequest, GetChangesRequest, this.context, ::compareRequest)
        checkProtoBufConversion(this.getChangesMaxRequest, GetChangesRequest, this.context, ::compareRequest)
    }

    @Test
    fun testJsonConversion() {
        checkJsonConversion(this.getChangesRequest, GetChangesRequest, this.context, ::compareRequest)
        checkJsonConversion(this.getChangesMaxRequest, GetChangesRequest, this.context, ::compareRequest)
    }

    private fun compareRequest(converted: GetChangesRequest<*, *>, original: GetChangesRequest<*, *>) {
        converted.keys.contentDeepEquals(original.keys) shouldBe true
        converted.dataModel shouldBe original.dataModel
        converted.filter shouldBe original.filter
        converted.order shouldBe original.order
        converted.filterSoftDeleted shouldBe original.filterSoftDeleted
        converted.toVersion shouldBe original.toVersion
        converted.fromVersion shouldBe original.fromVersion
    }
}