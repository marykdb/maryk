package maryk.core.query

import maryk.core.models.SingleTypedValueDataModel
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.definitions.EmbeddedObjectDefinition
import maryk.core.properties.definitions.ListDefinition
import maryk.core.query.Orders.Properties.orders
import maryk.core.values.ObjectValues

/** Defines multiple orders into one object */
data class Orders(
    val orders: List<Order>
) {
    constructor(vararg order: Order): this(order.toList())

    object Properties: ObjectPropertyDefinitions<Orders>() {
        val orders = add(
            1, "orders",
            ListDefinition(
                valueDefinition = EmbeddedObjectDefinition(
                    dataModel = { Order }
                )
            ),
            Orders::orders
        )
    }

    companion object: SingleTypedValueDataModel<List<Order>, Orders, Properties, RequestContext>(
        properties = Properties,
        singlePropertyDefinition = orders
    ) {
        override fun invoke(values: ObjectValues<Orders, Properties>) = Orders(
            orders = values(1)
        )
    }
}
