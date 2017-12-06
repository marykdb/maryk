package maryk.core.query.filters

import maryk.core.objects.QueryDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.PropertyDefinitions
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.definitions.wrapper.IsValuePropertyDefinitionWrapper
import maryk.core.properties.references.IsPropertyReference

/** Compares given regular expression against referenced property
 * @param reference to property to compare against
 * @param value the regex which the compared property should start with
 */
data class RegEx(
        override val reference: IsPropertyReference<String, IsValuePropertyDefinitionWrapper<String, IsPropertyContext, *>>,
        val regEx: String
) : IsPropertyCheck<String> {
    override val filterType = FilterType.REGEX

    companion object: QueryDataModel<RegEx>(
            properties = object : PropertyDefinitions<RegEx>() {
                init {
                    IsPropertyCheck.addReference(this, RegEx::reference)

                    add(1, "regEx", StringDefinition(
                            required = true
                    ), RegEx::regEx)
                }
            }
    ) {
        @Suppress("UNCHECKED_CAST")
        override fun invoke(map: Map<Int, *>) = RegEx(
                reference = map[0] as IsPropertyReference<String, IsValuePropertyDefinitionWrapper<String, IsPropertyContext, *>>,
                regEx = map[1] as String
        )
    }
}