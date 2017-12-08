package maryk.core.query.changes

import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.IsValueDefinition
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.definitions.PropertyDefinitions
import maryk.core.properties.definitions.contextual.ContextCaptureDefinition
import maryk.core.properties.definitions.contextual.ContextualPropertyReferenceDefinition
import maryk.core.properties.definitions.contextual.ContextualValueDefinition
import maryk.core.properties.definitions.wrapper.PropertyDefinitionWrapper
import maryk.core.properties.references.IsPropertyReference
import maryk.core.query.DataModelPropertyContext

/** An operation on a property
 * @param reference of property to operate on
 * @param valueToCompare (optional) if set the current value is checked against this value.
 * Operation will only complete if they both are equal
 * @param T: type of value to be operated on
 */
interface IsPropertyOperation<T: Any> : IsChange {
    val reference: IsPropertyReference<T, IsPropertyDefinition<T>>
    val valueToCompare: T?

    companion object {
        fun <DM: Any> addReference(definitions: PropertyDefinitions<DM>, getter: (DM) -> IsPropertyReference<*, *>?) {
            definitions.add(
                    0, "reference", ContextCaptureDefinition(
                            ContextualPropertyReferenceDefinition<DataModelPropertyContext>(
                                    contextualResolver = { it!!.dataModel!! }
                            )
                    ) { context, value ->
                        @Suppress("UNCHECKED_CAST")
                        context!!.reference = value as IsPropertyReference<*, PropertyDefinitionWrapper<*, *, *, *>>
                    },
                    getter
            )
        }

        fun <DM: Any> addValueToCompare(definitions: PropertyDefinitions<DM>, getter: (DM) -> Any?) {
            definitions.add(
                    1, "valueToCompare",
                    ContextualValueDefinition(
                            required = false,
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