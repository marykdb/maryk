package maryk.core.query.requests

import maryk.SimpleMarykObject
import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.core.properties.types.numeric.toUInt64
import maryk.core.query.DataModelPropertyContext
import maryk.core.query.Order
import maryk.core.query.filters.Exists
import kotlin.test.Test

class GetRequestTest {
    private val key1 = SimpleMarykObject.key.getKey(SimpleMarykObject("test1"))
    private val key2 = SimpleMarykObject.key.getKey(SimpleMarykObject("test2"))

    private val getRequest = GetRequest(
        SimpleMarykObject,
        key1,
        key2
    )

    private val getMaxRequest = GetRequest(
        SimpleMarykObject,
        key1,
        key2,
        filter = Exists(SimpleMarykObject.ref { value }),
        order = Order(SimpleMarykObject.ref { value }),
        toVersion = 333L.toUInt64(),
        filterSoftDeleted = true
    )

    private val context = DataModelPropertyContext(mapOf(
        SimpleMarykObject.name to SimpleMarykObject
    ))

    @Test
    fun testProtoBufConversion() {
        checkProtoBufConversion(this.getRequest, GetRequest, this.context)
        checkProtoBufConversion(this.getMaxRequest, GetRequest, this.context)
    }

    @Test
    fun testJsonConversion() {
        checkJsonConversion(this.getRequest, GetRequest, this.context)
        checkJsonConversion(this.getMaxRequest, GetRequest, this.context)
    }
}