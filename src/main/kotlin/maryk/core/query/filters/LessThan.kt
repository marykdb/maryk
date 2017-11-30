package maryk.core.query.filters

import maryk.core.objects.Def
import maryk.core.objects.QueryDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.AbstractValueDefinition
import maryk.core.properties.references.IsPropertyReference

/** Referenced value should be less than and not equal given value
 * @param reference to property to compare
 * @param value the value which is checked against
 * @param T: type of value to be operated on
 */
data class LessThan<T: Any>(
        override val reference: IsPropertyReference<T, AbstractValueDefinition<T, IsPropertyContext>>,
        override val value: T
) : IsPropertyComparison<T> {
    override val filterType = FilterType.LESS_THAN

    companion object: QueryDataModel<LessThan<*>>(
            definitions = listOf(
                    Def(IsPropertyCheck.Properties.reference, LessThan<*>::reference),
                    Def(IsPropertyComparison.Properties.value, LessThan<*>::value)
            )
    ) {
        @Suppress("UNCHECKED_CAST")
        override fun invoke(map: Map<Int, *>) = LessThan(
                reference = map[0] as IsPropertyReference<Any, AbstractValueDefinition<Any, IsPropertyContext>>,
                value = map[1] as Any
        )
    }
}