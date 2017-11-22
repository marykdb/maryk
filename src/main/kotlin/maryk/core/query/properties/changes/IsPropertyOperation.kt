package maryk.core.query.properties.changes

import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.AbstractValueDefinition
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.definitions.contextual.ContextCaptureDefinition
import maryk.core.properties.definitions.contextual.ContextualValueDefinition
import maryk.core.query.properties.DataModelPropertyContext
import maryk.core.properties.definitions.contextual.ContextualPropertyReferenceDefinition

/** An operation on a property
 * @param reference of property to operate on
 * @param valueToCompare (optional) if set the current value is checked against this value.
 * Operation will only complete if they both are equal
 * @param T: type of value to be operated on
 */
interface IsPropertyOperation<T: Any> : IsChange {
    val reference: IsPropertyReference<T, IsPropertyDefinition<T>>
    val valueToCompare: T?

    object Properties {
        val reference = ContextCaptureDefinition(
                ContextualPropertyReferenceDefinition<DataModelPropertyContext>(
                        name = "reference",
                        index = 0,
                        contextualResolver = { it!!.dataModel!! }
                )
        ) { context, value ->
            @Suppress("UNCHECKED_CAST")
            context!!.reference = value as IsPropertyReference<Any, AbstractValueDefinition<Any, IsPropertyContext>>
        }
        val valueToCompare = ContextualValueDefinition(
                name = "valueToCompare",
                index = 1,
                contextualResolver = { context: DataModelPropertyContext? ->
                    context!!.reference!!.propertyDefinition
                }
        )
    }
}