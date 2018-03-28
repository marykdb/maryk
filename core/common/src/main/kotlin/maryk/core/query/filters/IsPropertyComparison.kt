package maryk.core.query.filters

import maryk.core.exceptions.ContextNotFoundException
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.IsValueDefinition
import maryk.core.properties.definitions.PropertyDefinitions
import maryk.core.properties.definitions.contextual.ContextualValueDefinition
import maryk.core.query.DataModelPropertyContext

/** Comparison of property of type [T] */
interface IsPropertyComparison<T: Any>: IsPropertyCheck<T> {
    val value: T

    companion object {
        internal fun <DO: Any> addValue(definitions: PropertyDefinitions<DO>, getter: (DO) -> Any?) {
            definitions.add(
                1, "value",
                ContextualValueDefinition(
                    contextualResolver = { context: DataModelPropertyContext? ->
                        context?.reference?.let {
                            @Suppress("UNCHECKED_CAST")
                            it.propertyDefinition.definition as IsValueDefinition<Any, IsPropertyContext>
                        } ?: throw ContextNotFoundException()
                    }
                ),
                getter
            )
        }
    }
}