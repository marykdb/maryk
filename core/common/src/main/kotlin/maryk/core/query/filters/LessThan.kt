package maryk.core.query.filters

import maryk.core.objects.QueryDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.PropertyDefinitions
import maryk.core.properties.definitions.wrapper.IsValuePropertyDefinitionWrapper
import maryk.core.properties.references.IsPropertyReference

/** Referenced value [reference] should be less than and not equal given [value] of type [T] */
data class LessThan<T: Any>(
    override val reference: IsPropertyReference<T, IsValuePropertyDefinitionWrapper<T, IsPropertyContext, *>>,
    override val value: T
) : IsPropertyComparison<T> {
    override val filterType = FilterType.LESS_THAN

    internal companion object: QueryDataModel<LessThan<*>>(
        properties = object : PropertyDefinitions<LessThan<*>>() {
            init {
                IsPropertyCheck.addReference(this, LessThan<*>::reference)
                IsPropertyComparison.addValue(this, LessThan<*>::value)
            }
        }
    ) {
        @Suppress("UNCHECKED_CAST")
        override fun invoke(map: Map<Int, *>) = LessThan(
            reference = map[0] as IsPropertyReference<Any, IsValuePropertyDefinitionWrapper<Any, IsPropertyContext, *>>,
            value = map[1] as Any
        )
    }
}