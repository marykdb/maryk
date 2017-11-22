package maryk.core.query.changes

import maryk.core.objects.Def
import maryk.core.objects.QueryDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.AbstractValueDefinition
import maryk.core.properties.definitions.contextual.ContextualValueDefinition
import maryk.core.properties.references.IsPropertyReference
import maryk.core.query.DataModelPropertyContext

/** Value change for a property
 * @param reference to property affected by the change
 * @param newValue the value in which property is/was changed
 * @param valueToCompare (optional) if set the current value is checked against this value.
 * Operation will only complete if they both are equal
 * @param T: type of value to be operated on
 */
data class PropertyValueChange<T: Any>(
        override val reference: IsPropertyReference<T, AbstractValueDefinition<T, IsPropertyContext>>,
        val newValue: T,
        override val valueToCompare: T? = null
) : IsPropertyOperation<T> {
    object Properties {
        val newValue = ContextualValueDefinition(
                name = "newValue",
                index = 2,
                contextualResolver = { context: DataModelPropertyContext? ->
                    context!!.reference!!.propertyDefinition
                }
        )
    }

    companion object: QueryDataModel<PropertyValueChange<*>>(
            construct = {
                @Suppress("UNCHECKED_CAST")
                PropertyValueChange(
                    reference = it[0] as IsPropertyReference<Any, AbstractValueDefinition<Any, IsPropertyContext>>,
                    valueToCompare = it[1],
                    newValue = it[2] as Any
                )
            },
            definitions = listOf(
                    Def(IsPropertyOperation.Properties.reference, PropertyValueChange<*>::reference),
                    Def(IsPropertyOperation.Properties.valueToCompare, PropertyValueChange<*>::valueToCompare),
                    Def(Properties.newValue, PropertyValueChange<*>::newValue)
            )
    )
}