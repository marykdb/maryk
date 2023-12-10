package maryk.core.query.orders

import maryk.core.properties.definitions.EmbeddedObjectDefinition
import maryk.core.properties.enum.IndexedEnumComparable
import maryk.core.properties.enum.IndexedEnumDefinition
import maryk.core.properties.enum.IsCoreEnum
import maryk.core.properties.enum.TypeEnum
import maryk.core.query.orders.OrderType.ORDER
import maryk.core.query.orders.OrderType.ORDERS

/** Defines the type of order */
enum class OrderType(
    override val index: UInt,
    override val alternativeNames: Set<String>? = null
) : IndexedEnumComparable<OrderType>, TypeEnum<IsOrder>, IsCoreEnum {
    ORDER(1u),
    ORDERS(2u);

    companion object : IndexedEnumDefinition<OrderType>(
        OrderType::class, { entries }
    )
}

internal val mapOfOrderTypeToEmbeddedObject =
    mapOf(
        ORDER to EmbeddedObjectDefinition(dataModel = { Order.Model }),
        ORDERS to EmbeddedObjectDefinition(dataModel = { Orders })
    )
