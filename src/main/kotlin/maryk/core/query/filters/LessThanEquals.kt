package maryk.core.query.filters

import maryk.core.objects.Def
import maryk.core.objects.QueryDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.AbstractValueDefinition
import maryk.core.properties.references.IsPropertyReference

/** Referenced value should be less than or equal given value
 * @param reference to property to compare
 * @param value the value which is checked against
 * @param T: type of value to be operated on
 */
data class LessThanEquals<T: Any>(
        override val reference: IsPropertyReference<T, AbstractValueDefinition<T, IsPropertyContext>>,
        override val value: T
) : IsPropertyComparison<T> {
    override val filterType = FilterType.LESS_THAN_EQUALS

    companion object: QueryDataModel<LessThanEquals<*>>(
            construct = {
                @Suppress("UNCHECKED_CAST")
                LessThanEquals(
                        reference = it[0] as IsPropertyReference<Any, AbstractValueDefinition<Any, IsPropertyContext>>,
                        value = it[1] as Any
                )
            },
            definitions = listOf(
                    Def(IsPropertyCheck.Properties.reference, LessThanEquals<*>::reference),
                    Def(IsPropertyComparison.Properties.value, LessThanEquals<*>::value)
            )
    )
}