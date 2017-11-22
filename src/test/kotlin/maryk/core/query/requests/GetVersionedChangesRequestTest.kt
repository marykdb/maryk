package maryk.core.query.requests

import maryk.SubMarykObject
import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.core.properties.types.numeric.toUInt32
import maryk.core.properties.types.toUInt64
import maryk.core.query.properties.DataModelPropertyContext
import maryk.test.shouldBe
import kotlin.test.Test

class GetVersionedChangesRequestTest {
    private val key1 = SubMarykObject.key.getKey(SubMarykObject("test1"))
    private val key2 = SubMarykObject.key.getKey(SubMarykObject("test2"))

    private val getVersionedChangesRequest = GetVersionedChangesRequest(
            SubMarykObject,
            key1,
            key2,
            fromVersion = 1234L.toUInt64(),
            maxVersions = 5.toUInt32()
    )

    private val context = DataModelPropertyContext(mapOf(
            SubMarykObject.name to SubMarykObject
    ))

    @Test
    fun testProtoBufConversion() {
        checkProtoBufConversion(this.getVersionedChangesRequest, GetVersionedChangesRequest, this.context, ::compareRequest)
    }

    @Test
    fun testJsonConversion() {
        checkJsonConversion(this.getVersionedChangesRequest, GetVersionedChangesRequest, this.context, ::compareRequest)
    }

    private fun compareRequest(converted: GetVersionedChangesRequest<*, *>) {
        converted.keys.contentDeepEquals(this.getVersionedChangesRequest.keys) shouldBe true
        converted.dataModel shouldBe this.getVersionedChangesRequest.dataModel
        converted.filterSoftDeleted shouldBe this.getVersionedChangesRequest.filterSoftDeleted
        converted.toVersion shouldBe this.getVersionedChangesRequest.toVersion
        converted.fromVersion shouldBe this.getVersionedChangesRequest.fromVersion
        converted.maxVersions shouldBe this.getVersionedChangesRequest.maxVersions
    }
}