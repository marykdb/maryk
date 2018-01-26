package maryk.core.query.requests

import maryk.SimpleMarykObject
import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.core.properties.types.numeric.toUInt64
import maryk.core.query.DataModelPropertyContext
import maryk.core.query.Order
import maryk.core.query.filters.Exists
import kotlin.test.Test

class GetChangesRequestTest {
    private val key1 = SimpleMarykObject.key.getKey(SimpleMarykObject("test1"))
    private val key2 = SimpleMarykObject.key.getKey(SimpleMarykObject("test2"))

    private val getChangesRequest = GetChangesRequest(
        SimpleMarykObject,
        key1,
        key2,
        fromVersion = 1234L.toUInt64(),
        toVersion = 3456L.toUInt64()
    )

    private val getChangesMaxRequest = GetChangesRequest(
        SimpleMarykObject,
        key1,
        key2,
        filter = Exists(SimpleMarykObject.ref { value }),
        order = Order(SimpleMarykObject.ref { value }),
        fromVersion = 1234L.toUInt64(),
        toVersion = 3456L.toUInt64(),
        filterSoftDeleted = true
    )

    private val context = DataModelPropertyContext(mapOf(
        SimpleMarykObject.name to SimpleMarykObject
    ))

    @Test
    fun testProtoBufConversion() {
        checkProtoBufConversion(this.getChangesRequest, GetChangesRequest, this.context)
        checkProtoBufConversion(this.getChangesMaxRequest, GetChangesRequest, this.context)
    }

    @Test
    fun testJsonConversion() {
        checkJsonConversion(this.getChangesRequest, GetChangesRequest, this.context)
        checkJsonConversion(this.getChangesMaxRequest, GetChangesRequest, this.context)
    }
}