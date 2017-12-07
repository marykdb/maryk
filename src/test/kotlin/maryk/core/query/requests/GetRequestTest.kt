package maryk.core.query.requests

import maryk.SubMarykObject
import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.core.properties.types.toUInt64
import maryk.core.query.DataModelPropertyContext
import maryk.core.query.Order
import maryk.core.query.filters.Exists
import kotlin.test.Test

class GetRequestTest {
    private val key1 = SubMarykObject.key.getKey(SubMarykObject("test1"))
    private val key2 = SubMarykObject.key.getKey(SubMarykObject("test2"))

    private val getRequest = GetRequest(
            SubMarykObject,
            key1,
            key2
    )

    private val getMaxRequest = GetRequest(
            SubMarykObject,
            key1,
            key2,
            filter = Exists(SubMarykObject.ref { value }),
            order = Order(SubMarykObject.ref { value }),
            toVersion = 333L.toUInt64(),
            filterSoftDeleted = true
    )

    private val context = DataModelPropertyContext(mapOf(
            SubMarykObject.name to SubMarykObject
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