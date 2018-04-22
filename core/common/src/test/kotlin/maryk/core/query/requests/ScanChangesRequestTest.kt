package maryk.core.query.requests

import maryk.SimpleMarykObject
import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.core.properties.types.numeric.toUInt32
import maryk.core.properties.types.numeric.toUInt64
import maryk.core.query.DataModelPropertyContext
import maryk.core.query.Order
import maryk.core.query.filters.exists
import kotlin.test.Test

class ScanChangesRequestTest {
    private val key1 = SimpleMarykObject.key(SimpleMarykObject("test1"))

    private val scanChangesRequest = SimpleMarykObject.scanChanges(
        startKey = key1,
        fromVersion = 1234L.toUInt64()
    )

    private val scanChangeMaxRequest = SimpleMarykObject.scanChanges(
        startKey = key1,
        filter = SimpleMarykObject.ref { value }.exists(),
        order = Order(SimpleMarykObject.ref { value }),
        limit = 100.toUInt32(),
        filterSoftDeleted = true,
        toVersion = 2345L.toUInt64(),
        fromVersion = 1234L.toUInt64()
    )

    private val context = DataModelPropertyContext(mapOf(
        SimpleMarykObject.name to SimpleMarykObject
    ))

    @Test
    fun testProtoBufConversion() {
        checkProtoBufConversion(this.scanChangesRequest, ScanChangesRequest, this.context)
        checkProtoBufConversion(this.scanChangeMaxRequest, ScanChangesRequest, this.context)
    }

    @Test
    fun testJsonConversion() {
        checkJsonConversion(this.scanChangesRequest, ScanChangesRequest, this.context)
        checkJsonConversion(this.scanChangeMaxRequest, ScanChangesRequest, this.context)
    }
}
