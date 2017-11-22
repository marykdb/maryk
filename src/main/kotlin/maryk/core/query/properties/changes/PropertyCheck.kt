package maryk.core.query.properties.changes

import maryk.core.objects.Def
import maryk.core.objects.QueryDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.AbstractValueDefinition
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.references.IsPropertyReference

/** Value change for a property
 * @param reference to property affected by the change
 * @param newValue the value in which property is/was changed
 * @param valueToCompare (optional) if set the current value is checked against this value.
 * Operation will only complete if they both are equal
 * @param T: type of value to be operated on
 */
data class PropertyCheck<T: Any>(
        override val reference: IsPropertyReference<T, IsPropertyDefinition<T>>,
        override val valueToCompare: T? = null
) : IsPropertyOperation<T> {
    companion object: QueryDataModel<PropertyCheck<*>>(
            construct = {
                @Suppress("UNCHECKED_CAST")
                PropertyCheck(
                        reference = it[0] as IsPropertyReference<Any, AbstractValueDefinition<Any, IsPropertyContext>>,
                        valueToCompare = it[1]
                )
            },
            definitions = listOf(
                    Def(IsPropertyOperation.Properties.reference, PropertyCheck<*>::reference),
                    Def(IsPropertyOperation.Properties.valueToCompare, PropertyCheck<*>::valueToCompare)
            )
    )
}
