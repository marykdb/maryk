package maryk.core.query.requests

import maryk.SubMarykObject
import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.core.properties.types.numeric.toUInt32
import maryk.core.properties.types.toUInt64
import maryk.core.query.DataModelPropertyContext
import maryk.core.query.Order
import maryk.core.query.filters.Exists
import maryk.test.shouldBe
import kotlin.test.Test

class GetVersionedChangesRequestTest {
    private val key1 = SubMarykObject.key.getKey(SubMarykObject("test1"))
    private val key2 = SubMarykObject.key.getKey(SubMarykObject("test2"))

    private val getVersionedChangesRequest = GetVersionedChangesRequest(
            SubMarykObject,
            key1,
            key2,
            fromVersion = 1234L.toUInt64()
    )

    private val getVersionedChangesMaxRequest = GetVersionedChangesRequest(
            SubMarykObject,
            key1,
            key2,
            filter = Exists(SubMarykObject.Properties.value.getRef()),
            order = Order(SubMarykObject.Properties.value.getRef()),
            fromVersion = 1234L.toUInt64(),
            toVersion = 12345L.toUInt64(),
            maxVersions = 5.toUInt32(),
            filterSoftDeleted = true
    )

    private val context = DataModelPropertyContext(mapOf(
            SubMarykObject.name to SubMarykObject
    ))

    @Test
    fun testProtoBufConversion() {
        checkProtoBufConversion(this.getVersionedChangesRequest, GetVersionedChangesRequest, this.context, ::compareRequest)
        checkProtoBufConversion(this.getVersionedChangesMaxRequest, GetVersionedChangesRequest, this.context, ::compareRequest)
    }

    @Test
    fun testJsonConversion() {
        checkJsonConversion(this.getVersionedChangesRequest, GetVersionedChangesRequest, this.context, ::compareRequest)
        checkJsonConversion(this.getVersionedChangesMaxRequest, GetVersionedChangesRequest, this.context, ::compareRequest)
    }

    private fun compareRequest(converted: GetVersionedChangesRequest<*, *>, original: GetVersionedChangesRequest<*, *>) {
        converted.keys.contentDeepEquals(original.keys) shouldBe true
        converted.dataModel shouldBe original.dataModel
        converted.filter shouldBe original.filter
        converted.order shouldBe original.order
        converted.filterSoftDeleted shouldBe original.filterSoftDeleted
        converted.toVersion shouldBe original.toVersion
        converted.fromVersion shouldBe original.fromVersion
        converted.maxVersions shouldBe original.maxVersions
    }
}