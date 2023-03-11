package maryk.core.query

import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.extensions.toUnitLambda
import maryk.core.query.orders.Direction.DESC
import maryk.core.query.orders.Order
import maryk.test.models.SimpleMarykModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.expect

class OrderTest {
    private val order = Order(
        SimpleMarykModel { value::ref },
        DESC
    )

    private val orderDefault = Order()
    private val orderDesc = Order(direction = DESC)

    private val context = RequestContext(
        mapOf(
            SimpleMarykModel.Model.name toUnitLambda { SimpleMarykModel.Model }
        ),
        dataModel = SimpleMarykModel.Model
    )

    @Test
    fun testOrder() {
        expect(DESC) { this.order.direction }
        expect(SimpleMarykModel { value::ref }) { this.order.propertyReference }
    }

    @Test
    fun convertToProtoBufAndBack() {
        checkProtoBufConversion(this.order, Order.Model, { this.context }, ::compareRequest)
    }

    @Test
    fun convertToJSONAndBack() {
        checkJsonConversion(this.order, Order.Model.Model, { this.context }, ::compareRequest)
    }

    @Test
    fun convertToYAMLAndBack() {
        expect(
            """
            !Desc value
            """.trimIndent()
        ) {
            checkYamlConversion(this.order, Order.Model.Model, { this.context }, ::compareRequest)
        }
    }

    @Test
    fun convertDefaultToProtoBufAndBack() {
        checkProtoBufConversion(this.orderDefault, Order.Model, { this.context }, ::compareRequest)
    }

    @Test
    fun convertDefaultToJSONAndBack() {
        checkJsonConversion(this.orderDefault, Order.Model.Model, { this.context }, ::compareRequest)
    }

    @Test
    fun convertDefaultToYAMLAndBack() {
        expect("!Asc") {
            checkYamlConversion(this.orderDefault, Order.Model.Model, { this.context }, ::compareRequest)
        }
    }

    @Test
    fun convertDescToProtoBufAndBack() {
        checkProtoBufConversion(this.orderDesc, Order.Model, { this.context }, ::compareRequest)
    }

    @Test
    fun convertDescToJSONAndBack() {
        checkJsonConversion(this.orderDesc, Order.Model.Model, { this.context }, ::compareRequest)
    }

    @Test
    fun convertDescToYAMLAndBack() {
        expect(
            """
            !Desc
            """.trimIndent()
        ) {
            checkYamlConversion(this.orderDesc, Order.Model.Model, { this.context }, ::compareRequest)
        }
    }

    private fun compareRequest(converted: Order, original: Order) {
        assertEquals(original.propertyReference, converted.propertyReference)
        assertEquals(original.direction, converted.direction)
    }
}
