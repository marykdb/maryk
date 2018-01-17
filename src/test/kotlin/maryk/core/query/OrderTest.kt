package maryk.core.query

import maryk.SimpleMarykObject
import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.core.objects.RootDataModel
import maryk.core.properties.definitions.PropertyDefinitions
import maryk.test.shouldBe
import kotlin.test.Test

class OrderTest {
    private val order = Order(
            SimpleMarykObject.ref { value },
            Direction.ASC
    )

    @Suppress("UNCHECKED_CAST")
    private val context = DataModelPropertyContext(
            mapOf(
                    SimpleMarykObject.name to SimpleMarykObject
            ),
            dataModel = SimpleMarykObject as RootDataModel<Any, PropertyDefinitions<Any>>
    )

    @Test
    fun testOrder() {
        this.order.direction shouldBe Direction.ASC
        this.order.propertyReference shouldBe SimpleMarykObject.ref { value }
    }

    @Test
    fun testProtoBufConversion() {
        checkProtoBufConversion(this.order, Order, this.context, ::compareRequest)
    }

    @Test
    fun testJsonConversion() {
        checkJsonConversion(this.order, Order, this.context, ::compareRequest)
    }

    private fun compareRequest(converted: Order, original: Order) {
        converted.propertyReference shouldBe original.propertyReference
        converted.direction shouldBe original.direction
    }
}