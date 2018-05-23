package maryk.core.query.filters

import maryk.core.objects.ReferencePairDataModel
import maryk.core.objects.ReferenceValuePairsPropertyDefinitions
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.wrapper.IsValuePropertyDefinitionWrapper
import maryk.core.properties.references.IsPropertyReference
import maryk.core.query.DataModelPropertyContext
import maryk.core.query.pairs.ReferenceValuePair
import maryk.core.query.pairs.with
import maryk.json.IsJsonLikeWriter

/** Compares given [prefix] string against referenced property */
infix fun IsPropertyReference<String, IsValuePropertyDefinitionWrapper<String, *, IsPropertyContext, *>>.isPrefixedBy(
    prefix: String
) = Prefix(this with prefix)

/** Referenced values in [referenceValuePairs] should match with prefixes */
data class Prefix(
    val referenceValuePairs: List<ReferenceValuePair<String>>
) : IsFilter {
    override val filterType = FilterType.Prefix

    constructor(vararg referenceValuePair: ReferenceValuePair<String>): this(referenceValuePair.toList())

    internal object Properties : ReferenceValuePairsPropertyDefinitions<String, Prefix>() {
        override val referenceValuePairs = ReferenceValuePair.addReferenceValuePairsDefinition(
            this, Prefix::referenceValuePairs
        )
    }

    internal companion object: ReferencePairDataModel<String, Prefix>(
        properties = Properties
    ) {
        override fun invoke(map: Map<Int, *>) = Prefix(
            referenceValuePairs = map(0)
        )

        override fun writeJson(obj: Prefix, writer: IsJsonLikeWriter, context: DataModelPropertyContext?) {
            writer.writeJsonMapObject(obj.referenceValuePairs, context)
        }
    }
}
