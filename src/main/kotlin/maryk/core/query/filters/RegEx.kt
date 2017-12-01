package maryk.core.query.filters

import maryk.core.objects.Def
import maryk.core.objects.QueryDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.AbstractValueDefinition
import maryk.core.properties.definitions.PropertyDefinitions
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
    override val filterType = FilterType.REGEX

    internal object Properties : PropertyDefinitions<RegEx>() {
        val regEx = StringDefinition(
                name = "regEx",
                index = 1,
                required = true
        )
    }

    companion object: QueryDataModel<RegEx>(
            definitions = listOf(
                    Def(IsPropertyCheck.Properties.reference, RegEx::reference),
                    Def(Properties.regEx, RegEx::regEx)
            )
    ) {
        @Suppress("UNCHECKED_CAST")
        override fun invoke(map: Map<Int, *>) = RegEx(
                reference = map[0] as IsPropertyReference<String, StringDefinition>,
                regEx = map[1] as String
        )
    }
}