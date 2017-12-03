package maryk.core.query.filters

import maryk.core.objects.Def
import maryk.core.objects.QueryDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.AbstractValueDefinition
import maryk.core.properties.definitions.PropertyDefinitions
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

    internal object Properties : PropertyDefinitions<Prefix>() {
        val prefix = StringDefinition(
                name = "prefix",
                index = 1,
                required = true
        )
    }

    companion object: QueryDataModel<Prefix>(
            definitions = listOf(
                    Def(IsPropertyCheck.Properties.reference, Prefix::reference),
                    Def(Properties.prefix, Prefix::prefix)
            ),
            properties = object : PropertyDefinitions<Prefix>() {
                init {
                    IsPropertyCheck.addReference(this, Prefix::reference)
                    add(1, "prefix", StringDefinition(
                            required = true
                    ), Prefix::prefix)
                }
            }
    ) {
        @Suppress("UNCHECKED_CAST")
        override fun invoke(map: Map<Int, *>) = Prefix(
                reference = map[0] as IsPropertyReference<String, StringDefinition>,
                prefix = map[1] as String
        )
    }
}