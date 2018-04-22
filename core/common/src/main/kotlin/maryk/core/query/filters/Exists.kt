package maryk.core.query.filters

import maryk.core.objects.QueryDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.PropertyDefinitions
import maryk.core.properties.definitions.wrapper.IsValuePropertyDefinitionWrapper
import maryk.core.properties.references.IsPropertyReference

/** Checks if value exists */
fun <T: Any> IsPropertyReference<T, IsValuePropertyDefinitionWrapper<T, IsPropertyContext, *>>.exists() =
        Exists<T>(this)

/** Checks if [reference] to value of type [T] exists */
data class Exists<T: Any> internal constructor(
    override val reference: IsPropertyReference<T, IsValuePropertyDefinitionWrapper<T, IsPropertyContext, *>>
) : IsPropertyCheck<T> {
    override val filterType = FilterType.EXISTS

    internal companion object: QueryDataModel<Exists<*>>(
        properties = object : PropertyDefinitions<Exists<*>>() {
            init {
                IsPropertyCheck.addReference(this, Exists<*>::reference)
            }
        }
    ) {
        @Suppress("UNCHECKED_CAST")
        override fun invoke(map: Map<Int, *>) = Exists(
            reference = map[0] as IsPropertyReference<Any, IsValuePropertyDefinitionWrapper<Any, IsPropertyContext, *>>
        )
    }
}
