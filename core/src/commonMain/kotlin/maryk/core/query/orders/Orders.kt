package maryk.core.query.orders

import maryk.core.properties.SingleTypedValueModel
import maryk.core.properties.definitions.EmbeddedObjectDefinition
import maryk.core.properties.definitions.list
import maryk.core.query.RequestContext
import maryk.core.query.orders.OrderType.ORDERS
import maryk.core.query.orders.Orders.Companion.orders
import maryk.core.values.ObjectValues

/** Defines multiple orders into one object */
data class Orders(
    val orders: List<Order>
) : IsOrder {
    constructor(vararg order: Order) : this(order.toList())

    override val orderType = ORDERS

    companion object : SingleTypedValueModel<List<Order>, Orders, Companion, RequestContext>(
        singlePropertyDefinitionGetter = { orders }
    ) {
        val orders by list(
            index = 1u,
            getter = Orders::orders,
            valueDefinition = EmbeddedObjectDefinition(
                dataModel = { Order.Model.Model }
            )
        )

        override fun invoke(values: ObjectValues<Orders, Companion>) =
            Orders(
                orders = values(1u)
            )
    }
}
