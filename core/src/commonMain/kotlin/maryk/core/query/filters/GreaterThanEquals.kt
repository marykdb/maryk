package maryk.core.query.filters

import maryk.core.models.ReferencePairDataModel
import maryk.core.models.ReferenceValuePairsObjectPropertyDefinitions
import maryk.core.query.RequestContext
import maryk.core.query.pairs.ReferenceValuePair
import maryk.core.values.ObjectValues
import maryk.json.IsJsonLikeWriter

/** Referenced values in [referenceValuePairs] should be greater than and equal given value */
data class GreaterThanEquals(
    override val referenceValuePairs: List<ReferenceValuePair<Any>>
) : IsReferenceValuePairsFilter<Any> {
    override val filterType = FilterType.GreaterThanEquals

    @Suppress("UNCHECKED_CAST")
    constructor(vararg referenceValuePair: ReferenceValuePair<*>): this(referenceValuePair.toList() as List<ReferenceValuePair<Any>>)

    object Properties : ReferenceValuePairsObjectPropertyDefinitions<Any, GreaterThanEquals>() {
        override val referenceValuePairs = addReferenceValuePairsDefinition(GreaterThanEquals::referenceValuePairs)
    }

    companion object: ReferencePairDataModel<Any, GreaterThanEquals, Properties>(
        properties = Properties
    ) {
        override fun invoke(values: ObjectValues<GreaterThanEquals, Properties>) = GreaterThanEquals(
            referenceValuePairs = values(1)
        )

        override fun writeJson(obj: GreaterThanEquals, writer: IsJsonLikeWriter, context: RequestContext?) {
            writer.writeJsonMapObject(obj.referenceValuePairs, context)
        }
    }
}
