package maryk.core.query.filters

import maryk.core.objects.Def
import maryk.core.objects.QueryDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.AbstractValueDefinition
import maryk.core.properties.references.IsPropertyReference

/** Checks if reference exists
 * @param reference to property to compare against
 * @param T: type of value to be operated on
 */
data class Exists<T: Any>(
        override val reference: IsPropertyReference<T, AbstractValueDefinition<T, IsPropertyContext>>
) : IsPropertyCheck<T> {
    override val filterType = FilterType.EXISTS

    companion object: QueryDataModel<Exists<*>>(
            construct = {
                @Suppress("UNCHECKED_CAST")
                Exists(
                        reference = it[0] as IsPropertyReference<Any, AbstractValueDefinition<Any, IsPropertyContext>>
                )
            },
            definitions = listOf(
                    Def(IsPropertyCheck.Properties.reference, Exists<*>::reference)
            )
    )
}