package maryk.core.query.filters

import maryk.core.properties.definitions.contextual.ContextualValueDefinition
import maryk.core.query.DataModelPropertyContext

/** Property Comparison
 * @param T: type of value to be operated on
 */
interface IsPropertyComparison<T: Any>: IsPropertyCheck<T> {
    val value: T

    object Properties {
        val value = ContextualValueDefinition(
                name = "value",
                index = 1,
                contextualResolver = { context: DataModelPropertyContext? ->
                    context!!.reference!!.propertyDefinition
                }
        )
    }
}