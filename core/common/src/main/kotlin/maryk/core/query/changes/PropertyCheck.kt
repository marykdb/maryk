package maryk.core.query.changes

import maryk.core.objects.QueryDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.definitions.IsValueDefinition
import maryk.core.properties.definitions.PropertyDefinitions
import maryk.core.properties.references.IsPropertyReference

/**
 * Value check for a property of type [T]
 * Optionally compares against [valueToCompare] and will only succeed if values match
 */
data class PropertyCheck<T: Any>(
    override val reference: IsPropertyReference<T, IsPropertyDefinition<T>>,
    override val valueToCompare: T? = null
) : IsPropertyOperation<T> {
    override val changeType = ChangeType.PROP_CHECK

    internal companion object: QueryDataModel<PropertyCheck<*>>(
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