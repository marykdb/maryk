package maryk.core.query.filters

import maryk.core.models.ReferencePairDataModel
import maryk.core.models.ReferenceValuePairsPropertyDefinitions
import maryk.core.objects.ValueMap
import maryk.core.query.DataModelPropertyContext
import maryk.core.query.pairs.ReferenceValuePair
import maryk.json.IsJsonLikeWriter

/** Referenced values in [referenceValuePairs] should be greater than and equal given value */
data class GreaterThanEquals(
    val referenceValuePairs: List<ReferenceValuePair<Any>>
) : IsFilter {
    override val filterType = FilterType.GreaterThanEquals

    @Suppress("UNCHECKED_CAST")
    constructor(vararg referenceValuePair: ReferenceValuePair<*>): this(referenceValuePair.toList() as List<ReferenceValuePair<Any>>)

    internal object Properties : ReferenceValuePairsPropertyDefinitions<Any, GreaterThanEquals>() {
        override val referenceValuePairs = addReferenceValuePairsDefinition(GreaterThanEquals::referenceValuePairs)
    }

    internal companion object: ReferencePairDataModel<Any, GreaterThanEquals, Properties>(
        properties = Properties
    ) {
        override fun invoke(map: ValueMap<GreaterThanEquals, Properties>) = GreaterThanEquals(
            referenceValuePairs = map(0)
        )

        override fun writeJson(obj: GreaterThanEquals, writer: IsJsonLikeWriter, context: DataModelPropertyContext?) {
            writer.writeJsonMapObject(obj.referenceValuePairs, context)
        }
    }
}
