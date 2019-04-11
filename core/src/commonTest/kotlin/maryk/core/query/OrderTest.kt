package maryk.core.query

import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.extensions.toUnitLambda
import maryk.core.query.orders.Direction
import maryk.core.query.orders.Direction.DESC
import maryk.core.query.orders.Order
import maryk.test.models.SimpleMarykModel
import maryk.test.shouldBe
import kotlin.test.Test

class OrderTest {
    private val order = Order(
        SimpleMarykModel.ref { value },
        DESC
    )

    private val orderDefault = Order()
    private val orderDesc = Order(direction = DESC)

    private val context = RequestContext(
        mapOf(
            SimpleMarykModel.name toUnitLambda { SimpleMarykModel }
        ),
        dataModel = SimpleMarykModel
    )

    @Test
    fun testOrder() {
        this.order.direction shouldBe DESC
        this.order.propertyReference shouldBe SimpleMarykModel.ref { value }
    }

    @Test
    fun convertToProtoBufAndBack() {
        checkProtoBufConversion(this.order, Order, { this.context }, ::compareRequest)
    }

    @Test
    fun convertToJSONAndBack() {
        checkJsonConversion(this.order, Order, { this.context }, ::compareRequest)
    }

    @Test
    fun convertToYAMLAndBack() {
        checkYamlConversion(this.order, Order, { this.context }, ::compareRequest) shouldBe """
        !Desc value
        """.trimIndent()
    }

    @Test
    fun convertDefaultToProtoBufAndBack() {
        checkProtoBufConversion(this.orderDefault, Order, { this.context }, ::compareRequest)
    }

    @Test
    fun convertDefaultToJSONAndBack() {
        checkJsonConversion(this.orderDefault, Order, { this.context }, ::compareRequest)
    }

    @Test
    fun convertDefaultToYAMLAndBack() {
        checkYamlConversion(this.orderDefault, Order, { this.context }, ::compareRequest) shouldBe "!Asc"
    }

    @Test
    fun convertDescToProtoBufAndBack() {
        checkProtoBufConversion(this.orderDesc, Order, { this.context }, ::compareRequest)
    }

    @Test
    fun convertDescToJSONAndBack() {
        checkJsonConversion(this.orderDesc, Order, { this.context }, ::compareRequest)
    }

    @Test
    fun convertDescToYAMLAndBack() {
        checkYamlConversion(this.orderDesc, Order, { this.context }, ::compareRequest) shouldBe """
        !Desc
        """.trimIndent()
    }

    private fun compareRequest(converted: Order, original: Order) {
        converted.propertyReference shouldBe original.propertyReference
        converted.direction shouldBe original.direction
    }
}
