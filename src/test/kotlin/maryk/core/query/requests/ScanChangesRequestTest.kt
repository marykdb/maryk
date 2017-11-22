package maryk.core.query.requests

import maryk.SubMarykObject
import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.core.properties.types.numeric.toUInt32
import maryk.core.properties.types.toUInt64
import maryk.core.query.properties.DataModelPropertyContext
import maryk.test.shouldBe
import kotlin.test.Test

class ScanChangesRequestTest {
    private val key1 = SubMarykObject.key.getKey(SubMarykObject("test1"))

    private val scanChangesRequest = ScanChangesRequest(
            SubMarykObject,
            startKey = key1,
            limit = 100.toUInt32(),
            toVersion = 2345L.toUInt64(),
            fromVersion = 1234L.toUInt64()
    )

    private val context = DataModelPropertyContext(mapOf(
            SubMarykObject.name to SubMarykObject
    ))

    @Test
    fun testProtoBufConversion() {
        checkProtoBufConversion(this.scanChangesRequest, ScanChangesRequest, this.context, ::compareRequest)
    }

    @Test
    fun testJsonConversion() {
        checkJsonConversion(this.scanChangesRequest, ScanChangesRequest, this.context, ::compareRequest)
    }

    private fun compareRequest(converted: ScanChangesRequest<*, *>) {
        converted.startKey shouldBe this.scanChangesRequest.startKey
        converted.dataModel shouldBe this.scanChangesRequest.dataModel
        converted.filterSoftDeleted shouldBe this.scanChangesRequest.filterSoftDeleted
        converted.toVersion shouldBe this.scanChangesRequest.toVersion
        converted.fromVersion shouldBe this.scanChangesRequest.fromVersion
    }
}