package maryk.core.query.filters

import maryk.core.models.ReferencePairDataModel
import maryk.core.models.ReferenceValuePairsObjectPropertyDefinitions
import maryk.core.query.RequestContext
import maryk.core.query.pairs.ReferenceValuePair
import maryk.core.values.ObjectValues
import maryk.json.IsJsonLikeWriter

/** Referenced values in [referenceValuePairs] should be greater than and not equal given value */
data class GreaterThan internal constructor(
    override val referenceValuePairs: List<ReferenceValuePair<Any>>
) : IsReferenceValuePairsFilter<Any> {
    override val filterType = FilterType.GreaterThan

    @Suppress("UNCHECKED_CAST")
    constructor(vararg referenceValuePair: ReferenceValuePair<*>): this(referenceValuePair.toList() as List<ReferenceValuePair<Any>>)

    object Properties : ReferenceValuePairsObjectPropertyDefinitions<Any, GreaterThan>() {
        override val referenceValuePairs = addReferenceValuePairsDefinition(GreaterThan::referenceValuePairs)
    }

    companion object: ReferencePairDataModel<Any, GreaterThan, Properties>(
        properties = Properties
    ) {
        override fun invoke(values: ObjectValues<GreaterThan, Properties>) = GreaterThan(
            referenceValuePairs = values(1)
        )

        override fun writeJson(obj: GreaterThan, writer: IsJsonLikeWriter, context: RequestContext?) {
            writer.writeJsonMapObject(obj.referenceValuePairs, context)
        }
    }
}
