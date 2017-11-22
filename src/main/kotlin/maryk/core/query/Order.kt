package maryk.core.query

import maryk.core.objects.Def
import maryk.core.objects.QueryDataModel
import maryk.core.properties.definitions.EnumDefinition
import maryk.core.properties.definitions.contextual.ContextualPropertyReferenceDefinition
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.types.IndexedEnum

/** Direction Enumeration */
enum class Direction(override val index: Int) : IndexedEnum<Direction> {
    ASC(0), DESC(1)
}

/** To define the order of results
 * @param propertyReference to property to order on
 * @param direction of ordering
 */
data class Order(
        val propertyReference: IsPropertyReference<*, *>,
        val direction: Direction = Direction.ASC
) {
    object Properties {
        val propertyReference = ContextualPropertyReferenceDefinition<DataModelPropertyContext>(
                name = "propertyReference",
                index = 0,
                contextualResolver = { it!!.dataModel!! }
        )
        val direction = EnumDefinition(
                name = "direction",
                index = 1,
                required = true,
                values = Direction.values()
        )
    }

    companion object: QueryDataModel<Order>(
            construct = {
                @Suppress("UNCHECKED_CAST")
                Order(
                        propertyReference = it[0] as IsPropertyReference<*, *>,
                        direction = it[1] as Direction
                )
            },
            definitions = listOf(
                    Def(Properties.propertyReference, Order::propertyReference),
                    Def(Properties.direction, Order::direction)
            )
    )
}
