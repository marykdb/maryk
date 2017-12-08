package maryk.core.query.changes

import maryk.core.objects.QueryDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.IsValueDefinition
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.definitions.PropertyDefinitions
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
    override val changeType = ChangeType.PROP_CHECK

    companion object: QueryDataModel<PropertyCheck<*>>(
            properties = object : PropertyDefinitions<PropertyCheck<*>>() {
                init {
                    IsPropertyOperation.addReference(this, PropertyCheck<*>::reference)
                    IsPropertyOperation.addValueToCompare(this, PropertyCheck<*>::valueToCompare)
                }
            }
    ) {
        @Suppress("UNCHECKED_CAST")
        override fun invoke(map: Map<Int, *>) = PropertyCheck(
                reference = map[0] as IsPropertyReference<Any, IsValueDefinition<Any, IsPropertyContext>>,
                valueToCompare = map[1]
        )
    }
}
