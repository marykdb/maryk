package maryk.core.query.filters

import maryk.core.models.ReferencePairDataModel
import maryk.core.models.ReferenceValuePairsPropertyDefinitions
import maryk.core.objects.DataObjectMap
import maryk.core.query.DataModelPropertyContext
import maryk.core.query.pairs.ReferenceValuePair
import maryk.json.IsJsonLikeWriter

/** Referenced values in [referenceValuePairs] should be less than and not equal given value */
data class LessThanEquals internal constructor(
    val referenceValuePairs: List<ReferenceValuePair<Any>>
) : IsFilter {
    override val filterType = FilterType.LessThanEquals

    @Suppress("UNCHECKED_CAST")
    constructor(vararg referenceValuePair: ReferenceValuePair<out Any>): this(referenceValuePair.toList() as List<ReferenceValuePair<Any>>)

    internal object Properties : ReferenceValuePairsPropertyDefinitions<Any, LessThanEquals>() {
        override val referenceValuePairs = addReferenceValuePairsDefinition(LessThanEquals::referenceValuePairs)
    }

    internal companion object: ReferencePairDataModel<Any, LessThanEquals>(
        properties = Properties
    ) {
        override fun invoke(map: DataObjectMap<LessThanEquals>) = LessThanEquals(
            referenceValuePairs = map(0)
        )

        override fun writeJson(obj: LessThanEquals, writer: IsJsonLikeWriter, context: DataModelPropertyContext?) {
            writer.writeJsonMapObject(obj.referenceValuePairs, context)
        }
    }
}
