package maryk.core.query.filters

import maryk.core.objects.QueryDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.PropertyDefinitions
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.definitions.wrapper.IsValuePropertyDefinitionWrapper
import maryk.core.properties.references.IsPropertyReference

/** Compares given regular expression [regEx] against referenced property */
infix fun IsPropertyReference<String, IsValuePropertyDefinitionWrapper<String, *, IsPropertyContext, *>>.matchesRegEx(
    regEx: String
) = RegEx(this, regEx)

/** Compares given regular expression [regEx] against referenced property [reference] */
data class RegEx(
    override val reference: IsPropertyReference<String, IsValuePropertyDefinitionWrapper<String, *, IsPropertyContext, *>>,
    val regEx: String
) : IsPropertyCheck<String> {
    override val filterType = FilterType.RegEx

    internal companion object: QueryDataModel<RegEx>(
        properties = object : PropertyDefinitions<RegEx>() {
            init {
                IsPropertyCheck.addReference(this, RegEx::reference)

                add(1, "regEx", StringDefinition(), RegEx::regEx)
            }
        }
    ) {
        override fun invoke(map: Map<Int, *>) = RegEx(
            reference = map(0),
            regEx = map(1)
        )
    }
}
