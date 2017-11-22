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

class ScanRequestTest {
    private val key1 = SubMarykObject.key.getKey(SubMarykObject("test1"))

    private val scanRequest = ScanRequest(
            SubMarykObject,
            startKey = key1
    )

    private val scanMaxRequest = ScanRequest(
            SubMarykObject,
            startKey = key1,
            filter = Exists(SubMarykObject.Properties.value.getRef()),
            order = Order(SubMarykObject.Properties.value.getRef()),
            limit = 200.toUInt32(),
            filterSoftDeleted = true,
            toVersion = 2345L.toUInt64()
    )

    private val context = DataModelPropertyContext(mapOf(
            SubMarykObject.name to SubMarykObject
    ))

    @Test
    fun testProtoBufConversion() {
        checkProtoBufConversion(this.scanRequest, ScanRequest, this.context, ::compareRequest)
        checkProtoBufConversion(this.scanMaxRequest, ScanRequest, this.context, ::compareRequest)
    }

    @Test
    fun testJsonConversion() {
        checkJsonConversion(this.scanRequest, ScanRequest, this.context, ::compareRequest)
        checkJsonConversion(this.scanMaxRequest, ScanRequest, this.context, ::compareRequest)
    }

    private fun compareRequest(converted: ScanRequest<*, *>, expected: ScanRequest<*, *>) {
        converted.startKey shouldBe expected.startKey
        converted.dataModel shouldBe expected.dataModel
        converted.filter shouldBe expected.filter
        converted.order shouldBe expected.order
        converted.limit shouldBe expected.limit
        converted.filterSoftDeleted shouldBe expected.filterSoftDeleted
        converted.toVersion shouldBe expected.toVersion
    }
}