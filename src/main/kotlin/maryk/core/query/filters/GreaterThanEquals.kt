package maryk.core.query.filters

import maryk.core.objects.QueryDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.PropertyDefinitions
import maryk.core.properties.definitions.wrapper.IsValuePropertyDefinitionWrapper
import maryk.core.properties.references.IsPropertyReference

/** Referenced value [reference] should be greater than or equal given [value] of type [T] */
data class GreaterThanEquals<T: Any>(
        override val reference: IsPropertyReference<T, IsValuePropertyDefinitionWrapper<T, IsPropertyContext, *>>,
        override val value: T
) : IsPropertyComparison<T> {
    override val filterType = FilterType.GREATER_THAN_EQUALS

    companion object: QueryDataModel<GreaterThanEquals<*>>(
            properties = object : PropertyDefinitions<GreaterThanEquals<*>>() {
                init {
                    IsPropertyCheck.addReference(this, GreaterThanEquals<*>::reference)
                    IsPropertyComparison.addValue(this, GreaterThanEquals<*>::value)
                }
            }
    ) {
        @Suppress("UNCHECKED_CAST")
        override fun invoke(map: Map<Int, *>) = GreaterThanEquals(
                reference = map[0] as IsPropertyReference<Any, IsValuePropertyDefinitionWrapper<Any, IsPropertyContext, *>>,
                value = map[1] as Any
        )
    }
}