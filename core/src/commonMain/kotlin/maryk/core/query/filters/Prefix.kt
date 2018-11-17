package maryk.core.query.filters

import maryk.core.models.ReferencePairDataModel
import maryk.core.models.ReferenceValuePairsObjectPropertyDefinitions
import maryk.core.values.ObjectValues
import maryk.core.query.RequestContext
import maryk.core.query.pairs.ReferenceValuePair
import maryk.json.IsJsonLikeWriter

/** Referenced values in [referenceValuePairs] should match with prefixes */
data class Prefix internal constructor(
    val referenceValuePairs: List<ReferenceValuePair<String>>
) : IsFilter {
    override val filterType = FilterType.Prefix

    constructor(vararg referenceValuePair: ReferenceValuePair<String>): this(referenceValuePair.toList())

    object Properties : ReferenceValuePairsObjectPropertyDefinitions<String, Prefix>() {
        override val referenceValuePairs = addReferenceValuePairsDefinition(Prefix::referenceValuePairs)
    }

    companion object: ReferencePairDataModel<String, Prefix, Properties>(
        properties = Properties
    ) {
        override fun invoke(map: ObjectValues<Prefix, Properties>) = Prefix(
            referenceValuePairs = map(1)
        )

        override fun writeJson(obj: Prefix, writer: IsJsonLikeWriter, context: RequestContext?) {
            writer.writeJsonMapObject(obj.referenceValuePairs, context)
        }
    }
}
