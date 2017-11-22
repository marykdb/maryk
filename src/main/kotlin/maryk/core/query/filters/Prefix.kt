package maryk.core.query.filters

import maryk.core.objects.Def
import maryk.core.objects.QueryDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.AbstractValueDefinition
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.references.IsPropertyReference

/** Compares given prefix string against referenced property
 * @param reference to property to compare against
 * @param value the value which the compared property should start with
 */
data class Prefix(
        override val reference: IsPropertyReference<String, AbstractValueDefinition<String, IsPropertyContext>>,
        val prefix: String
) : IsPropertyCheck<String> {
    override val filterType = FilterType.PREFIX

    object Properties {
        val prefix = StringDefinition(
                name = "prefix",
                index = 1,
                required = true
        )
    }

    companion object: QueryDataModel<Prefix>(
            construct = {
                @Suppress("UNCHECKED_CAST")
                Prefix(
                        reference = it[0] as IsPropertyReference<String, StringDefinition>,
                        prefix = it[1] as String
                )
            },
            definitions = listOf(
                    Def(IsPropertyCheck.Properties.reference, Prefix::reference),
                    Def(Properties.prefix, Prefix::prefix)
            )
    )
}