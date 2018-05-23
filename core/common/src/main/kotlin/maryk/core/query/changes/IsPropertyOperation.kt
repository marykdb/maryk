package maryk.core.query.changes

import maryk.core.exceptions.ContextNotFoundException
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.IsValueDefinition
import maryk.core.properties.definitions.PropertyDefinitions
import maryk.core.properties.definitions.contextual.ContextualValueDefinition
import maryk.core.query.DataModelPropertyContext
import maryk.core.query.DefinedByReference

/** An operation on a property of type [T] */
interface IsPropertyOperation<T: Any> : IsChange, DefinedByReference<T> {
    val valueToCompare: T?

    companion object {
        internal fun <DO: Any> addValueToCompare(definitions: PropertyDefinitions<DO>, getter: (DO) -> Any?) {
            definitions.add(
                1, "valueToCompare",
                ContextualValueDefinition(
                    required = false,
                    contextualResolver = { context: DataModelPropertyContext? ->
                        context?.reference?.let {
                            @Suppress("UNCHECKED_CAST")
                            it.propertyDefinition.definition as IsValueDefinition<Any, IsPropertyContext>
                        }?: throw ContextNotFoundException()
                    }
                ),
                getter
            )
        }
    }
}
