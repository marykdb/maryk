package maryk.core.query.requests

import maryk.SubMarykObject
import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.core.properties.types.numeric.toUInt32
import maryk.core.properties.types.toUInt64
import maryk.core.query.DataModelPropertyContext
import maryk.core.query.Order
import maryk.core.query.filters.Exists
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
        checkProtoBufConversion(this.scanRequest, ScanRequest, this.context)
        checkProtoBufConversion(this.scanMaxRequest, ScanRequest, this.context)
    }

    @Test
    fun testJsonConversion() {
        checkJsonConversion(this.scanRequest, ScanRequest, this.context)
        checkJsonConversion(this.scanMaxRequest, ScanRequest, this.context)
    }
}