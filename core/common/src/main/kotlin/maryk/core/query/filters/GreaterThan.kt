package maryk.core.query.filters

import maryk.core.objects.QueryDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.PropertyDefinitions
import maryk.core.properties.definitions.wrapper.IsValuePropertyDefinitionWrapper
import maryk.core.properties.references.IsPropertyReference

/** Referenced value should be greater than and not equal given [value] of type [T] */
infix fun <T: Any> IsPropertyReference<T, IsValuePropertyDefinitionWrapper<T, *, IsPropertyContext, *>>.greaterThan(
    value: T
) = GreaterThan(this, value)

/** Referenced value [reference] should be greater than and not equal given [value] of type [T] */
data class GreaterThan<T: Any>(
    override val reference: IsPropertyReference<T, IsValuePropertyDefinitionWrapper<T, *, IsPropertyContext, *>>,
    override val value: T
) : IsPropertyComparison<T> {
    override val filterType = FilterType.GreaterThan

    internal companion object: QueryDataModel<GreaterThan<*>>(
        properties = object : PropertyDefinitions<GreaterThan<*>>() {
            init {
                IsPropertyCheck.addReference(this, GreaterThan<*>::reference)
                IsPropertyComparison.addValue(this, GreaterThan<*>::value)
            }
        }
    ) {
        override fun invoke(map: Map<Int, *>) = GreaterThan(
            reference = map(0),
            value = map(1)
        )
    }
}
