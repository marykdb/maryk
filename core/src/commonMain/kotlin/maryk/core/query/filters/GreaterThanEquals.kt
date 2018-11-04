package maryk.core.query.filters

import maryk.core.models.ReferencePairDataModel
import maryk.core.models.ReferenceValuePairsObjectPropertyDefinitions
import maryk.core.objects.ObjectValues
import maryk.core.query.RequestContext
import maryk.core.query.pairs.ReferenceValuePair
import maryk.json.IsJsonLikeWriter

/** Referenced values in [referenceValuePairs] should be greater than and equal given value */
data class GreaterThanEquals(
    val referenceValuePairs: List<ReferenceValuePair<Any>>
) : IsFilter {
    override val filterType = FilterType.GreaterThanEquals

    @Suppress("UNCHECKED_CAST")
    constructor(vararg referenceValuePair: ReferenceValuePair<*>): this(referenceValuePair.toList() as List<ReferenceValuePair<Any>>)

    object Properties : ReferenceValuePairsObjectPropertyDefinitions<Any, GreaterThanEquals>() {
        override val referenceValuePairs = addReferenceValuePairsDefinition(GreaterThanEquals::referenceValuePairs)
    }

    companion object: ReferencePairDataModel<Any, GreaterThanEquals, Properties>(
        properties = Properties
    ) {
        override fun invoke(map: ObjectValues<GreaterThanEquals, Properties>) = GreaterThanEquals(
            referenceValuePairs = map(1)
        )

        override fun writeJson(obj: GreaterThanEquals, writer: IsJsonLikeWriter, context: RequestContext?) {
            writer.writeJsonMapObject(obj.referenceValuePairs, context)
        }
    }
}