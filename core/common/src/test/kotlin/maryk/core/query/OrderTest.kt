package maryk.core.query

import maryk.SimpleMarykModel
import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.test.shouldBe
import kotlin.test.Test

class OrderTest {
    private val order = Order(
        SimpleMarykModel.ref { value },
        Direction.DESC
    )

    private val context = RequestContext(
        mapOf(
            SimpleMarykModel.name to { SimpleMarykModel }
        ),
        dataModel = SimpleMarykModel
    )

    @Test
    fun testOrder() {
        this.order.direction shouldBe Direction.DESC
        this.order.propertyReference shouldBe SimpleMarykModel.ref { value }
    }

    @Test
    fun convert_to_ProtoBuf_and_back() {
        checkProtoBufConversion(this.order, Order, { this.context }, ::compareRequest)
    }

    @Test
    fun convert_to_JSON_and_back() {
        checkJsonConversion(this.order, Order, { this.context }, ::compareRequest)
    }

    @Test
    fun convert_to_YAML_and_back() {
        checkYamlConversion(this.order, Order, { this.context }, ::compareRequest) shouldBe """
        !Desc value
        """.trimIndent()
    }

    private fun compareRequest(converted: Order, original: Order) {
        converted.propertyReference shouldBe original.propertyReference
        converted.direction shouldBe original.direction
    }
}
