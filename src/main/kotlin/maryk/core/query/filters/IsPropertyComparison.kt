package maryk.core.query.filters

import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.AbstractValueDefinition
import maryk.core.properties.definitions.PropertyDefinitions
import maryk.core.properties.definitions.contextual.ContextualValueDefinition
import maryk.core.query.DataModelPropertyContext

/** Property Comparison
 * @param T: type of value to be operated on
 */
interface IsPropertyComparison<T: Any>: IsPropertyCheck<T> {
    val value: T

    companion object {
        fun <DM: Any> addValue(definitions: PropertyDefinitions<DM>, getter: (DM) -> Any?) {
            definitions.add(
                    1, "value",
                    ContextualValueDefinition(
                            contextualResolver = { context: DataModelPropertyContext? ->
                                @Suppress("UNCHECKED_CAST")
                                context!!.reference!!.propertyDefinition.definition as AbstractValueDefinition<Any, IsPropertyContext>
                            }
                    ),
                    getter
            )
        }
    }
}