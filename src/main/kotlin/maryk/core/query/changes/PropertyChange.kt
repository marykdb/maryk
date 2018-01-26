package maryk.core.query.changes

import maryk.core.objects.QueryDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.IsValueDefinition
import maryk.core.properties.definitions.PropertyDefinitions
import maryk.core.properties.definitions.contextual.ContextualValueDefinition
import maryk.core.properties.definitions.wrapper.IsValuePropertyDefinitionWrapper
import maryk.core.properties.references.IsPropertyReference
import maryk.core.query.DataModelPropertyContext

/**
 * Change value to [newValue] for property of type [T] referred by [reference]
 * Optionally compares against [valueToCompare] and will only change value if values match
 */
data class PropertyChange<T: Any>(
    override val reference: IsPropertyReference<T, IsValuePropertyDefinitionWrapper<T, IsPropertyContext, *>>,
    val newValue: T,
    override val valueToCompare: T? = null
) : IsPropertyOperation<T> {
    override val changeType = ChangeType.PROP_CHANGE

    internal companion object: QueryDataModel<PropertyChange<*>>(
        properties = object : PropertyDefinitions<PropertyChange<*>>() {
            init {
                IsPropertyOperation.addReference(this, PropertyChange<*>::reference)
                IsPropertyOperation.addValueToCompare(this, PropertyChange<*>::valueToCompare)

                add(2, "newValue", ContextualValueDefinition(
                    contextualResolver = { context: DataModelPropertyContext? ->
                        @Suppress("UNCHECKED_CAST")
                        context!!.reference!!.propertyDefinition.definition as IsValueDefinition<Any, IsPropertyContext>
                    }
                ), PropertyChange<*>::newValue)
            }
        }
    ) {
        @Suppress("UNCHECKED_CAST")
        override fun invoke(map: Map<Int, *>) = PropertyChange(
            reference = map[0] as IsPropertyReference<Any, IsValuePropertyDefinitionWrapper<Any, IsPropertyContext, Any>>,
            valueToCompare = map[1],
            newValue = map[2] as Any
        )
    }
}