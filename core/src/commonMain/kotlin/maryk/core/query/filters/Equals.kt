package maryk.core.query.filters

import maryk.core.models.ReferencePairDataModel
import maryk.core.models.ReferenceValuePairsObjectPropertyDefinitions
import maryk.core.query.RequestContext
import maryk.core.query.pairs.ReferenceValuePair
import maryk.core.values.ObjectValues
import maryk.json.IsJsonLikeWriter

/** Referenced values in [referenceValuePairs] should be equal given value */
data class Equals internal constructor(
    override val referenceValuePairs: List<ReferenceValuePair<Any>>
) : IsReferenceValuePairsFilter<Any> {
    override val filterType = FilterType.Equals

    @Suppress("UNCHECKED_CAST")
    constructor(vararg referenceValuePair: ReferenceValuePair<*>): this(referenceValuePair.toList() as List<ReferenceValuePair<Any>>)

    object Properties: ReferenceValuePairsObjectPropertyDefinitions<Any, Equals>() {
        override val referenceValuePairs = addReferenceValuePairsDefinition(Equals::referenceValuePairs)
    }

    companion object: ReferencePairDataModel<Any, Equals, Properties>(
        properties = Properties
    ) {
        override fun invoke(values: ObjectValues<Equals, Properties>) = Equals(
            referenceValuePairs = values(1)
        )

        override fun writeJson(obj: Equals, writer: IsJsonLikeWriter, context: RequestContext?) {
            writer.writeJsonMapObject(obj.referenceValuePairs, context)
        }
    }
}
