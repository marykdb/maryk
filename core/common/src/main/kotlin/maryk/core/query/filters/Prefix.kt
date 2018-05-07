package maryk.core.query.filters

import maryk.core.objects.QueryDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.PropertyDefinitions
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.definitions.wrapper.IsValuePropertyDefinitionWrapper
import maryk.core.properties.references.IsPropertyReference

/** Compares given [prefix] string against referenced property */
infix fun IsPropertyReference<String, IsValuePropertyDefinitionWrapper<String, *, IsPropertyContext, *>>.isPrefixedBy(
    prefix: String
) = Prefix(this, prefix)

/** Compares given [prefix] string against referenced property [reference] */
data class Prefix(
    override val reference: IsPropertyReference<String, IsValuePropertyDefinitionWrapper<String, *, IsPropertyContext, *>>,
    val prefix: String
) : IsPropertyCheck<String> {
    override val filterType = FilterType.Prefix

    internal companion object: QueryDataModel<Prefix>(
        properties = object : PropertyDefinitions<Prefix>() {
            init {
                IsPropertyCheck.addReference(this, Prefix::reference)
                add(1, "prefix", StringDefinition(), Prefix::prefix)
            }
        }
    ) {
        override fun invoke(map: Map<Int, *>) = Prefix(
            reference = map(0),
            prefix = map(1)
        )
    }
}
