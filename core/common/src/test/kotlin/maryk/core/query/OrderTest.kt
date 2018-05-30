package maryk.core.query

import maryk.SimpleMarykObject
import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.objects.RootDataModel
import maryk.core.properties.definitions.PropertyDefinitions
import maryk.test.shouldBe
import kotlin.test.Test

class OrderTest {
    private val order = Order(
        SimpleMarykObject.ref { value },
        Direction.DESC
    )

    @Suppress("UNCHECKED_CAST")
    private val context = DataModelPropertyContext(
        mapOf(
            SimpleMarykObject.name to { SimpleMarykObject }
        ),
        dataModel = SimpleMarykObject as RootDataModel<Any, PropertyDefinitions<Any>>
    )

    @Test
    fun testOrder() {
        this.order.direction shouldBe Direction.DESC
        this.order.propertyReference shouldBe SimpleMarykObject.ref { value }
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
