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

class GetVersionedChangesRequestTest {
    private val key1 = SimpleMarykObject.key.getKey(SimpleMarykObject("test1"))
    private val key2 = SimpleMarykObject.key.getKey(SimpleMarykObject("test2"))

    private val getVersionedChangesRequest = GetVersionedChangesRequest(
        SimpleMarykObject,
        key1,
        key2,
        fromVersion = 1234L.toUInt64()
    )

    private val getVersionedChangesMaxRequest = GetVersionedChangesRequest(
        SimpleMarykObject,
        key1,
        key2,
        filter = Exists(SimpleMarykObject.ref { value }),
        order = Order(SimpleMarykObject.ref { value }),
        fromVersion = 1234L.toUInt64(),
        toVersion = 12345L.toUInt64(),
        maxVersions = 5.toUInt32(),
        filterSoftDeleted = true
    )

    private val context = DataModelPropertyContext(mapOf(
        SimpleMarykObject.name to SimpleMarykObject
    ))

    @Test
    fun testProtoBufConversion() {
        checkProtoBufConversion(this.getVersionedChangesRequest, GetVersionedChangesRequest, this.context)
        checkProtoBufConversion(this.getVersionedChangesMaxRequest, GetVersionedChangesRequest, this.context)
    }

    @Test
    fun testJsonConversion() {
        checkJsonConversion(this.getVersionedChangesRequest, GetVersionedChangesRequest, this.context)
        checkJsonConversion(this.getVersionedChangesMaxRequest, GetVersionedChangesRequest, this.context)
    }
}