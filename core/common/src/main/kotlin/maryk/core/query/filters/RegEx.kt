package maryk.core.query.filters

import maryk.core.objects.SimpleFilterDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.PropertyDefinitions
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.definitions.wrapper.IsValuePropertyDefinitionWrapper
import maryk.core.properties.definitions.wrapper.PropertyDefinitionWrapper
import maryk.core.properties.references.IsPropertyReference
import maryk.core.query.DataModelPropertyContext
import maryk.json.IsJsonLikeWriter

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

    internal object Properties : PropertyDefinitions<RegEx>() {
        val reference = IsPropertyCheck.addReference(this, RegEx::reference)
        val regEx = add(1, "regEx", StringDefinition(), RegEx::regEx)
    }

    internal companion object: SimpleFilterDataModel<RegEx>(
        properties = Properties
    ) {
        override fun invoke(map: Map<Int, *>) = RegEx(
            reference = map(0),
            regEx = map(1)
        )

        override fun writeJson(obj: RegEx, writer: IsJsonLikeWriter, context: DataModelPropertyContext?) {
            @Suppress("UNCHECKED_CAST")
            writer.writeJsonValues(
                Properties.reference,
                obj.reference,
                Properties.regEx as PropertyDefinitionWrapper<Any, *, IsPropertyContext, *, *>,
                obj.regEx,
                context
            )
        }
    }
}
