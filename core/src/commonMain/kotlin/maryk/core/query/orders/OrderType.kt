package maryk.core.query.orders

import maryk.core.properties.definitions.EmbeddedObjectDefinition
import maryk.core.properties.enum.IndexedEnum
import maryk.core.properties.enum.IndexedEnumDefinition
import maryk.core.query.orders.OrderType.ORDER
import maryk.core.query.orders.OrderType.ORDERS

/** Defines the type of order */
enum class OrderType(
    override val index: UInt
) : IndexedEnum<OrderType> {
    ORDER(1u),
    ORDERS(2u);

    companion object : IndexedEnumDefinition<OrderType>(
        "OrderType", OrderType::values
    )
}

internal val mapOfOrderTypeToEmbeddedObject =
    mapOf(
        ORDER to EmbeddedObjectDefinition(dataModel = { Order }),
        ORDERS to EmbeddedObjectDefinition(dataModel = { Orders })
    )
