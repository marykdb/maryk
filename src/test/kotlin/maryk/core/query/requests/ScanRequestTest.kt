package maryk.core.query.requests

import maryk.SimpleMarykObject
import maryk.SubMarykObject
import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.core.properties.types.numeric.toUInt32
import maryk.core.properties.types.numeric.toUInt64
import maryk.core.query.DataModelPropertyContext
import maryk.core.query.Order
import maryk.core.query.filters.Exists
import kotlin.test.Test

class ScanRequestTest {
    private val key1 = SimpleMarykObject.key.getKey(SimpleMarykObject("test1"))

    private val scanRequest = ScanRequest(
            SimpleMarykObject,
            startKey = key1
    )

    private val scanMaxRequest = ScanRequest(
            SimpleMarykObject,
            startKey = key1,
            filter = Exists(SubMarykObject.ref { value }),
            order = Order(SubMarykObject.ref { value }),
            limit = 200.toUInt32(),
            filterSoftDeleted = true,
            toVersion = 2345L.toUInt64()
    )

    private val context = DataModelPropertyContext(mapOf(
            SimpleMarykObject.name to SimpleMarykObject
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