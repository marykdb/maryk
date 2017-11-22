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

class ScanVersionedChangesRequestTest {
    private val key1 = SubMarykObject.key.getKey(SubMarykObject("test1"))

    private val scanVersionedChangesRequest = ScanVersionedChangesRequest(
            SubMarykObject,
            startKey = key1,
            fromVersion = 1234L.toUInt64()
    )

    private val scanVersionedChangesMaxRequest = ScanVersionedChangesRequest(
            SubMarykObject,
            startKey = key1,
            filter = Exists(SubMarykObject.Properties.value.getRef()),
            order = Order(SubMarykObject.Properties.value.getRef()),
            limit = 300.toUInt32(),
            toVersion = 2345L.toUInt64(),
            fromVersion = 1234L.toUInt64(),
            maxVersions = 10.toUInt32()
    )

    private val context = DataModelPropertyContext(mapOf(
            SubMarykObject.name to SubMarykObject
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