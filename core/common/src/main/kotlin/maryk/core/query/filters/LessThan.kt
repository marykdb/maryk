package maryk.core.query.filters

import maryk.core.objects.QueryDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.PropertyDefinitions
import maryk.core.properties.definitions.wrapper.IsValuePropertyDefinitionWrapper
import maryk.core.properties.references.IsPropertyReference

/** Referenced value should be less than and not equalgiven [value] of type [T] */
infix fun <T: Any> IsPropertyReference<T, IsValuePropertyDefinitionWrapper<T, IsPropertyContext, *>>.lessThan(
    value: T
) = LessThan(this, value)

/** Referenced value [reference] should be less than and not equal given [value] of type [T] */
data class LessThan<T: Any> internal constructor(
    override val reference: IsPropertyReference<T, IsValuePropertyDefinitionWrapper<T, IsPropertyContext, *>>,
    override val value: T
) : IsPropertyComparison<T> {
    override val filterType = FilterType.LessThan

    internal companion object: QueryDataModel<LessThan<*>>(
        properties = object : PropertyDefinitions<LessThan<*>>() {
            init {
                IsPropertyCheck.addReference(this, LessThan<*>::reference)
                IsPropertyComparison.addValue(this, LessThan<*>::value)
            }
        }
    ) {
        override fun invoke(map: Map<Int, *>) = LessThan(
            reference = map(0),
            value = map(1)
        )
    }
}
