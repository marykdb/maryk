package maryk.core.query

import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.extensions.toUnitLambda
import maryk.core.query.orders.Order
import maryk.core.query.orders.Orders
import maryk.core.query.orders.ascending
import maryk.core.query.orders.descending
import maryk.test.models.SimpleMarykModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.expect

class OrdersTest {
    private val orders = Orders(
        SimpleMarykModel { value::ref }.descending(),
        SimpleMarykModel { value::ref }.ascending(),
        Order.ascending,
        Order.descending
    )

    private val context = RequestContext(
        mapOf(
            SimpleMarykModel.Model.name toUnitLambda { SimpleMarykModel.Model }
        ),
        dataModel = SimpleMarykModel.Model
    )

    @Test
    fun convertToProtoBufAndBack() {
        checkProtoBufConversion(this.orders, Orders, { this.context }, ::compareRequest)
    }

    @Test
    fun convertToJSONAndBack() {
        expect(
            """
            [{
              "propertyReference": "value",
              "direction": "DESC"
            }, {
              "propertyReference": "value",
              "direction": "ASC"
            }, {
              "direction": "ASC"
            }, {
              "direction": "DESC"
            }]
            """.trimIndent()
        ) {
            checkJsonConversion(this.orders, Orders, { this.context }, ::compareRequest)
        }
    }

    @Test
    fun convertToYAMLAndBack() {
        expect(
            """
            - !Desc value
            - value
            - !Asc
            - !Desc
            """.trimIndent()
        ) {
            checkYamlConversion(this.orders, Orders, { this.context }, ::compareRequest)
        }
    }

    private fun compareRequest(converted: Orders, original: Orders) {
        for ((first, second) in converted.orders.zip(original.orders)) {
            assertEquals(second.propertyReference, first.propertyReference)
            assertEquals(second.direction, first.direction)
        }
    }
}
