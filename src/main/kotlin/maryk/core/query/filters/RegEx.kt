package maryk.core.query.filters

import maryk.core.objects.Def
import maryk.core.objects.QueryDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.AbstractValueDefinition
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.references.IsPropertyReference

/** Compares given regular expression against referenced property
 * @param reference to property to compare against
 * @param value the regex which the compared property should start with
 */
data class RegEx(
        override val reference: IsPropertyReference<String, AbstractValueDefinition<String, IsPropertyContext>>,
        val regEx: String
) : IsPropertyCheck<String> {
    object Properties {
        val regEx = StringDefinition(
                name = "regEx",
                index = 1,
                required = true
        )
    }

    companion object: QueryDataModel<RegEx>(
            construct = {
                @Suppress("UNCHECKED_CAST")
                RegEx(
                        reference = it[0] as IsPropertyReference<String, StringDefinition>,
                        regEx = it[1] as String
                )
            },
            definitions = listOf(
                    Def(IsPropertyCheck.Properties.reference, RegEx::reference),
                    Def(Properties.regEx, RegEx::regEx)
            )
    )
}