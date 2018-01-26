package maryk.core.query.changes

import maryk.core.objects.QueryDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.definitions.IsValueDefinition
import maryk.core.properties.definitions.PropertyDefinitions
import maryk.core.properties.references.IsPropertyReference

/**
 * Delete of a property of type [T] referred by [reference]
 * Optionally compares against [valueToCompare] and will only delete if matches
 */
data class PropertyDelete<T: Any>(
    override val reference: IsPropertyReference<T, IsPropertyDefinition<T>>,
    override val valueToCompare: T? = null
) : IsPropertyOperation<T> {
    override val changeType = ChangeType.PROP_DELETE

    internal companion object: QueryDataModel<PropertyDelete<*>>(
        properties = object : PropertyDefinitions<PropertyDelete<*>>() {
            init {
                IsPropertyOperation.addReference(this, PropertyDelete<*>::reference)
                IsPropertyOperation.addValueToCompare(this, PropertyDelete<*>::valueToCompare)
            }
        }
    ) {
        @Suppress("UNCHECKED_CAST")
        override fun invoke(map: Map<Int, *>) = PropertyDelete(
            reference = map[0] as IsPropertyReference<Any, IsValueDefinition<Any, IsPropertyContext>>,
            valueToCompare = map[1]
        )
    }
}