package maryk.core.query.filters

import maryk.core.objects.Def
import maryk.core.objects.QueryDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.AbstractValueDefinition
import maryk.core.properties.definitions.PropertyDefinitions
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
            definitions = listOf(
                    Def(IsPropertyCheck.Properties.reference, Exists<*>::reference)
            ),
            properties = object : PropertyDefinitions<Exists<*>>() {
                init {
                    IsPropertyCheck.addReference(this, Exists<*>::reference)
                }
            }
    ) {
        @Suppress("UNCHECKED_CAST")
        override fun invoke(map: Map<Int, *>) = Exists(
                reference = map[0] as IsPropertyReference<Any, AbstractValueDefinition<Any, IsPropertyContext>>
        )
    }
}