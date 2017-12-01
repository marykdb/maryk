package maryk.core.query.changes

import maryk.core.objects.Def
import maryk.core.objects.QueryDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.AbstractValueDefinition
import maryk.core.properties.definitions.PropertyDefinitions
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
data class PropertyChange<T: Any>(
        override val reference: IsPropertyReference<T, AbstractValueDefinition<T, IsPropertyContext>>,
        val newValue: T,
        override val valueToCompare: T? = null
) : IsPropertyOperation<T> {
    override val changeType = ChangeType.PROP_CHANGE

    internal object Properties : PropertyDefinitions<PropertyChange<*>>() {
        val newValue = ContextualValueDefinition(
                name = "newValue",
                index = 2,
                contextualResolver = { context: DataModelPropertyContext? ->
                    context!!.reference!!.propertyDefinition
                }
        )
    }

    companion object: QueryDataModel<PropertyChange<*>>(
            definitions = listOf(
                    Def(IsPropertyOperation.Properties.reference, PropertyChange<*>::reference),
                    Def(IsPropertyOperation.Properties.valueToCompare, PropertyChange<*>::valueToCompare),
                    Def(Properties.newValue, PropertyChange<*>::newValue)
            )
    ) {
        @Suppress("UNCHECKED_CAST")
        override fun invoke(map: Map<Int, *>) = PropertyChange(
                reference = map[0] as IsPropertyReference<Any, AbstractValueDefinition<Any, IsPropertyContext>>,
                valueToCompare = map[1],
                newValue = map[2] as Any
        )
    }
}