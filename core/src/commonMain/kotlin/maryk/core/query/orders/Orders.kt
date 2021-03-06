package maryk.core.query.orders

import maryk.core.models.SingleTypedValueDataModel
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.definitions.EmbeddedObjectDefinition
import maryk.core.properties.definitions.list
import maryk.core.query.RequestContext
import maryk.core.query.orders.OrderType.ORDERS
import maryk.core.query.orders.Orders.Properties.orders
import maryk.core.values.ObjectValues

/** Defines multiple orders into one object */
data class Orders(
    val orders: List<Order>
) : IsOrder {
    constructor(vararg order: Order) : this(order.toList())

    override val orderType = ORDERS

    object Properties : ObjectPropertyDefinitions<Orders>() {
        val orders by list(
            index = 1u,
            getter = Orders::orders,
            valueDefinition = EmbeddedObjectDefinition(
                dataModel = { Order }
            )
        )
    }

    companion object : SingleTypedValueDataModel<List<Order>, Orders, Properties, RequestContext>(
        properties = Properties,
        singlePropertyDefinition = orders
    ) {
        override fun invoke(values: ObjectValues<Orders, Properties>) =
            Orders(
                orders = values(1u)
            )
    }
}
