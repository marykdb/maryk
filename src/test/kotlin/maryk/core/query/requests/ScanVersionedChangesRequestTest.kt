package maryk.core.query.requests

import maryk.SubMarykObject
import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.core.properties.types.numeric.toUInt32
import maryk.core.properties.types.toUInt64
import maryk.core.query.properties.DataModelPropertyContext
import maryk.test.shouldBe
import kotlin.test.Test

class ScanVersionedChangesRequestTest {
    private val key1 = SubMarykObject.key.getKey(SubMarykObject("test1"))

    private val scanVersionedChangesRequest = ScanVersionedChangesRequest(
            SubMarykObject,
            startKey = key1,
            limit = 100.toUInt32(),
            toVersion = 2345L.toUInt64(),
            fromVersion = 1234L.toUInt64(),
            maxVersions = 10.toUInt32()
    )

    private val context = DataModelPropertyContext(mapOf(
            SubMarykObject.name to SubMarykObject
    ))

    @Test
    fun testProtoBufConversion() {
        checkProtoBufConversion(this.scanVersionedChangesRequest, ScanVersionedChangesRequest, this.context, ::compareRequest)
    }

    @Test
    fun testJsonConversion() {
        checkJsonConversion(this.scanVersionedChangesRequest, ScanVersionedChangesRequest, this.context, ::compareRequest)
    }

    private fun compareRequest(converted: ScanVersionedChangesRequest<*, *>) {
        converted.startKey shouldBe this.scanVersionedChangesRequest.startKey
        converted.dataModel shouldBe this.scanVersionedChangesRequest.dataModel
        converted.filterSoftDeleted shouldBe this.scanVersionedChangesRequest.filterSoftDeleted
        converted.toVersion shouldBe this.scanVersionedChangesRequest.toVersion
        converted.fromVersion shouldBe this.scanVersionedChangesRequest.fromVersion
        converted.maxVersions shouldBe this.scanVersionedChangesRequest.maxVersions
    }
}