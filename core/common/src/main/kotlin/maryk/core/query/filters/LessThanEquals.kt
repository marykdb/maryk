package maryk.core.query.filters

import maryk.core.objects.QueryDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.PropertyDefinitions
import maryk.core.properties.definitions.wrapper.IsValuePropertyDefinitionWrapper
import maryk.core.properties.references.IsPropertyReference

/** Referenced value should be less than or equal given [value] of type [T] */
infix fun <T: Any> IsPropertyReference<T, IsValuePropertyDefinitionWrapper<T, *, IsPropertyContext, *>>.lessThanEquals(
    value: T
) = LessThanEquals(this, value)

/** Referenced value [reference] should be less than or equal given [value] of type [T] */
data class LessThanEquals<T: Any> internal constructor(
    override val reference: IsPropertyReference<T, IsValuePropertyDefinitionWrapper<T, *, IsPropertyContext, *>>,
    override val value: T
) : IsPropertyComparison<T> {
    override val filterType = FilterType.LessThanEquals

    internal companion object: QueryDataModel<LessThanEquals<*>>(
        properties = object : PropertyDefinitions<LessThanEquals<*>>() {
            init {
                IsPropertyCheck.addReference(this, LessThanEquals<*>::reference)
                IsPropertyComparison.addValue(this, LessThanEquals<*>::value)
            }
        }
    ) {
        override fun invoke(map: Map<Int, *>) = LessThanEquals(
            reference = map(0),
            value = map(1)
        )
    }
}
