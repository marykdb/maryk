package maryk.core.query

import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.extensions.toUnitLambda
import maryk.test.models.SimpleMarykModel
import maryk.test.shouldBe
import kotlin.test.Test

class OrderTest {
    private val order = Order(
        SimpleMarykModel.ref { value },
        Direction.DESC
    )

    private val context = RequestContext(
        mapOf(
            SimpleMarykModel.name toUnitLambda { SimpleMarykModel }
        ),
        dataModel = SimpleMarykModel
    )

    @Test
    fun testOrder() {
        this.order.direction shouldBe Direction.DESC
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

    private fun compareRequest(converted: Order, original: Order) {
        converted.propertyReference shouldBe original.propertyReference
        converted.direction shouldBe original.direction
    }
}
