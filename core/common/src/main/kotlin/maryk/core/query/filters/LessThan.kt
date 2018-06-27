package maryk.core.query.filters

import maryk.core.models.ReferencePairDataModel
import maryk.core.models.ReferenceValuePairsPropertyDefinitions
import maryk.core.query.DataModelPropertyContext
import maryk.core.query.pairs.ReferenceValuePair
import maryk.json.IsJsonLikeWriter

/** Referenced values in [referenceValuePairs] should be less than and not equal given value */
data class LessThan internal constructor(
    val referenceValuePairs: List<ReferenceValuePair<Any>>
) : IsFilter {
    @Suppress("UNCHECKED_CAST")
    constructor(vararg referenceValuePair: ReferenceValuePair<*>): this(referenceValuePair.toList() as List<ReferenceValuePair<Any>>)

    override val filterType = FilterType.LessThan

    internal object Properties : ReferenceValuePairsPropertyDefinitions<Any, LessThan>() {
        override val referenceValuePairs = addReferenceValuePairsDefinition(LessThan::referenceValuePairs)
    }

    internal companion object: ReferencePairDataModel<Any, LessThan>(
        properties = Properties
    ) {
        override fun invoke(map: Map<Int, *>) = LessThan(
            referenceValuePairs = map(0)
        )

        override fun writeJson(obj: LessThan, writer: IsJsonLikeWriter, context: DataModelPropertyContext?) {
            writer.writeJsonMapObject(obj.referenceValuePairs, context)
        }
    }
}
