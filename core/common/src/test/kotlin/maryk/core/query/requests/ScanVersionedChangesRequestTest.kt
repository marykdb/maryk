package maryk.core.query.requests

import maryk.SimpleMarykObject
import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.core.properties.types.numeric.toUInt32
import maryk.core.properties.types.numeric.toUInt64
import maryk.core.query.DataModelPropertyContext
import maryk.core.query.Order
import maryk.core.query.filters.Exists
import kotlin.test.Test

class ScanVersionedChangesRequestTest {
    private val key1 = SimpleMarykObject.key(SimpleMarykObject("test1"))

    private val scanVersionedChangesRequest = SimpleMarykObject.scanVersionedChanges(
        startKey = key1,
        fromVersion = 1234L.toUInt64()
    )

    private val scanVersionedChangesMaxRequest = SimpleMarykObject.scanVersionedChanges(
        startKey = key1,
        filter = Exists(SimpleMarykObject.ref { value }),
        order = Order(SimpleMarykObject.ref { value }),
        limit = 300.toUInt32(),
        toVersion = 2345L.toUInt64(),
        fromVersion = 1234L.toUInt64(),
        maxVersions = 10.toUInt32()
    )

    private val context = DataModelPropertyContext(mapOf(
        SimpleMarykObject.name to SimpleMarykObject
    ))

    @Test
    fun testProtoBufConversion() {
        checkProtoBufConversion(this.scanVersionedChangesRequest, ScanVersionedChangesRequest, this.context)
        checkProtoBufConversion(this.scanVersionedChangesMaxRequest, ScanVersionedChangesRequest, this.context)
    }

    @Test
    fun testJsonConversion() {
        checkJsonConversion(this.scanVersionedChangesRequest, ScanVersionedChangesRequest, this.context)
        checkJsonConversion(this.scanVersionedChangesMaxRequest, ScanVersionedChangesRequest, this.context)
    }
}
