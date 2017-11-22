package maryk.core.query

import maryk.SubMarykObject
import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.core.objects.RootDataModel
import maryk.test.shouldBe
import kotlin.test.Test

class OrderTest {
    private val order = Order(
            SubMarykObject.Properties.value.getRef(),
            Direction.ASC
    )

    @Suppress("UNCHECKED_CAST")
    private val context = DataModelPropertyContext(
            mapOf(
                    SubMarykObject.name to SubMarykObject
            ),
            dataModel = SubMarykObject as RootDataModel<Any>
    )

    @Test
    fun testOrder() {
        this.order.direction shouldBe Direction.ASC
        this.order.propertyReference shouldBe SubMarykObject.Properties.value.getRef()
    }

    @Test
    fun testProtoBufConversion() {
        checkProtoBufConversion(this.order, Order, this.context, ::compareRequest)
    }

    @Test
    fun testJsonConversion() {
        checkJsonConversion(this.order, Order, this.context, ::compareRequest)
    }

    private fun compareRequest(converted: Order) {
        converted.propertyReference shouldBe this.order.propertyReference
        converted.direction shouldBe this.order.direction
    }
}