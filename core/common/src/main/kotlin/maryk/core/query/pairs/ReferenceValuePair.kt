package maryk.core.query.pairs

import maryk.core.objects.SimpleDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.PropertyDefinitions
import maryk.core.properties.definitions.wrapper.IsValuePropertyDefinitionWrapper
import maryk.core.properties.references.IsPropertyReference
import maryk.core.query.filters.IsPropertyCheck
import maryk.core.query.filters.IsPropertyComparison

/** Compares given [value] of type [T] against referenced value [reference] */
data class ReferenceValuePair<T: Any> internal constructor(
    val reference: IsPropertyReference<T, IsValuePropertyDefinitionWrapper<T, *, IsPropertyContext, *>>,
    val value: T
) {
    internal object Properties: PropertyDefinitions<ReferenceValuePair<*>>() {
        val reference = IsPropertyCheck.addReference(
            this,
            ReferenceValuePair<*>::reference
        )
        val value = IsPropertyComparison.addValue(
            this,
            ReferenceValuePair<*>::value
        )
    }

    internal companion object: SimpleDataModel<ReferenceValuePair<*>, Properties>(
        properties = Properties
    ) {
        override fun invoke(map: Map<Int, *>) = ReferenceValuePair(
            reference = map(0),
            value = map(1)
        )
    }
}

/** Convenience infix method to create Reference [value] pairs */
infix fun <T: Any> IsPropertyReference<T, IsValuePropertyDefinitionWrapper<T, *, IsPropertyContext, *>>.with(value: T) =
    ReferenceValuePair(this, value)
