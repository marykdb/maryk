package maryk.core.query

import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.extensions.toUnitLambda
import maryk.test.models.SimpleMarykModel
import maryk.test.shouldBe
import kotlin.test.Test

class OrdersTest {
    private val orders = Orders(
        SimpleMarykModel.ref { value }.descending(),
        SimpleMarykModel.ref { value }.ascending(),
        Order.ascending,
        Order.descending
    )

    private val context = RequestContext(
        mapOf(
            SimpleMarykModel.name toUnitLambda { SimpleMarykModel }
        ),
        dataModel = SimpleMarykModel
    )

    @Test
    fun convertToProtoBufAndBack() {
        checkProtoBufConversion(this.orders, Orders, { this.context }, ::compareRequest)
    }

    @Test
    fun convertToJSONAndBack() {
        checkJsonConversion(this.orders, Orders, { this.context }, ::compareRequest)
    }

    @Test
    fun convertToYAMLAndBack() {
        checkYamlConversion(this.orders, Orders, { this.context }, ::compareRequest) shouldBe """
        - !Desc value
        - value
        - !Asc
        - !Desc
        """.trimIndent()
    }

    private fun compareRequest(converted: Orders, original: Orders) {
        for ((first, second) in converted.orders.zip(original.orders)) {
            first.propertyReference shouldBe second.propertyReference
            first.direction shouldBe second.direction
        }
    }
}
