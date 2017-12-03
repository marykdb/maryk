package maryk.core.query

import maryk.core.objects.Def
import maryk.core.objects.QueryDataModel
import maryk.core.properties.definitions.EnumDefinition
import maryk.core.properties.definitions.PropertyDefinitions
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
    internal object Properties : PropertyDefinitions<Order>() {
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
            definitions = listOf(
                    Def(Properties.propertyReference, Order::propertyReference),
                    Def(Properties.direction, Order::direction)
            ),
            properties = object : PropertyDefinitions<Order>() {
                init {
                    add(0, "propertyReference", ContextualPropertyReferenceDefinition<DataModelPropertyContext>(
                            contextualResolver = { it!!.dataModel!! }
                    ), Order::propertyReference)

                    add(1, "direction", EnumDefinition(
                            required = true,
                            values = Direction.values()
                    ), Order::direction)
                }
            }
    ) {
        @Suppress("UNCHECKED_CAST")
        override fun invoke(map: Map<Int, *>) = Order(
                propertyReference = map[0] as IsPropertyReference<*, *>,
                direction = map[1] as Direction
        )
    }
}
