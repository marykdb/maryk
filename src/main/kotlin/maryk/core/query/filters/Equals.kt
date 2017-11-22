package maryk.core.query.filters

import maryk.core.objects.Def
import maryk.core.objects.QueryDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.AbstractValueDefinition
import maryk.core.properties.references.IsPropertyReference

/** Compares given value against referenced value
 * @param reference to property to compare against
 * @param value the value which should be equal
 * @param T: type of value to be operated on
 */
data class Equals<T: Any>(
        override val reference: IsPropertyReference<T, AbstractValueDefinition<T, IsPropertyContext>>,
        override val value: T
) : IsPropertyComparison<T> {
    override val filterType = FilterType.EQUALS

    companion object: QueryDataModel<Equals<*>>(
            construct = {
                @Suppress("UNCHECKED_CAST")
                Equals(
                        reference = it[0] as IsPropertyReference<Any, AbstractValueDefinition<Any, IsPropertyContext>>,
                        value = it[1] as Any
                )
            },
            definitions = listOf(
                    Def(IsPropertyCheck.Properties.reference, Equals<*>::reference),
                    Def(IsPropertyComparison.Properties.value, Equals<*>::value)
            )
    )
}