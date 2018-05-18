package maryk.core.query.filters

import maryk.core.objects.SimpleFilterDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.PropertyDefinitions
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.definitions.wrapper.IsValuePropertyDefinitionWrapper
import maryk.core.properties.references.IsPropertyReference
import maryk.core.query.DataModelPropertyContext
import maryk.json.IsJsonLikeWriter

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

    internal object Properties : PropertyDefinitions<Prefix>() {
        val reference = IsPropertyCheck.addReference(this, Prefix::reference)
        val prefix = add(1, "prefix", StringDefinition(), Prefix::prefix)
    }

    internal companion object: SimpleFilterDataModel<Prefix>(
        properties = Properties
    ) {
        override fun invoke(map: Map<Int, *>) = Prefix(
            reference = map(0),
            prefix = map(1)
        )

        override fun writeJson(obj: Prefix, writer: IsJsonLikeWriter, context: DataModelPropertyContext?) {
            writer.writeJsonValues(Properties.reference, obj.reference, Properties.prefix, obj.prefix, context)
        }
    }
}
