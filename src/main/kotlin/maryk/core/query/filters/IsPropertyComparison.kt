package maryk.core.query.filters

import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.IsValueDefinition
import maryk.core.properties.definitions.PropertyDefinitions
import maryk.core.properties.definitions.contextual.ContextualValueDefinition
import maryk.core.query.DataModelPropertyContext

/** Property Comparison
 * @param T: type of value to be operated on
 */
interface IsPropertyComparison<T: Any>: IsPropertyCheck<T> {
    val value: T

    companion object {
        fun <DO: Any> addValue(definitions: PropertyDefinitions<DO>, getter: (DO) -> Any?) {
            definitions.add(
                    1, "value",
                    ContextualValueDefinition(
                            contextualResolver = { context: DataModelPropertyContext? ->
                                @Suppress("UNCHECKED_CAST")
                                context!!.reference!!.propertyDefinition.definition as IsValueDefinition<Any, IsPropertyContext>
                            }
                    ),
                    getter
            )
        }
    }
}