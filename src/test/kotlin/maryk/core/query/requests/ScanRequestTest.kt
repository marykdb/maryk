package maryk.core.query.requests

import maryk.SubMarykObject
import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.core.properties.types.numeric.toUInt32
import maryk.core.properties.types.toUInt64
import maryk.core.query.properties.DataModelPropertyContext
import maryk.test.shouldBe
import kotlin.test.Test

class ScanRequestTest {
    private val key1 = SubMarykObject.key.getKey(SubMarykObject("test1"))

    private val scanRequest = ScanRequest(
            SubMarykObject,
            startKey = key1,
            limit = 100.toUInt32(),
            toVersion = 2345L.toUInt64()
    )

    private val context = DataModelPropertyContext(mapOf(
            SubMarykObject.name to SubMarykObject
    ))

    @Test
    fun testProtoBufConversion() {
        checkProtoBufConversion(this.scanRequest, ScanRequest, this.context, ::compareRequest)
    }

    @Test
    fun testJsonConversion() {
        checkJsonConversion(this.scanRequest, ScanRequest, this.context, ::compareRequest)
    }

    private fun compareRequest(converted: ScanRequest<*, *>) {
        converted.startKey shouldBe this.scanRequest.startKey
        converted.dataModel shouldBe this.scanRequest.dataModel
        converted.filterSoftDeleted shouldBe this.scanRequest.filterSoftDeleted
        converted.toVersion shouldBe this.scanRequest.toVersion
    }
}