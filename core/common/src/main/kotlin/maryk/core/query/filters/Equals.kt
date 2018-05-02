package maryk.core.query.filters

import maryk.core.objects.QueryDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.PropertyDefinitions
import maryk.core.properties.definitions.wrapper.IsValuePropertyDefinitionWrapper
import maryk.core.properties.references.IsPropertyReference

/** Compares given [value] of type [T] against referenced value */
infix fun <T: Any> IsPropertyReference<T, IsValuePropertyDefinitionWrapper<T, IsPropertyContext, *>>.equals(
    value: T
) = Equals(this, value)

/** Compares given [value] of type [T] against referenced value [reference] */
data class Equals<T: Any> internal constructor(
    override val reference: IsPropertyReference<T, IsValuePropertyDefinitionWrapper<T, IsPropertyContext, *>>,
    override val value: T
) : IsPropertyComparison<T> {
    override val filterType = FilterType.Equals

    internal companion object: QueryDataModel<Equals<*>>(
        properties = object : PropertyDefinitions<Equals<*>>() {
            init {
                IsPropertyCheck.addReference(this, Equals<*>::reference)
                IsPropertyComparison.addValue(this, Equals<*>::value)
            }
        }
    ) {
        override fun invoke(map: Map<Int, *>) = Equals(
            reference = map(0),
            value = map(1)
        )
    }
}
