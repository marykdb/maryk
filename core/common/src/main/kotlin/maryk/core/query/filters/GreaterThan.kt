package maryk.core.query.filters

import maryk.core.models.ReferencePairDataModel
import maryk.core.models.ReferenceValuePairsPropertyDefinitions
import maryk.core.query.DataModelPropertyContext
import maryk.core.query.pairs.ReferenceValuePair
import maryk.json.IsJsonLikeWriter

/** Referenced values in [referenceValuePairs] should be greater than and not equal given value */
data class GreaterThan internal constructor(
    val referenceValuePairs: List<ReferenceValuePair<Any>>
) : IsFilter {
    override val filterType = FilterType.GreaterThan

    @Suppress("UNCHECKED_CAST")
    constructor(vararg referenceValuePair: ReferenceValuePair<*>): this(referenceValuePair.toList() as List<ReferenceValuePair<Any>>)

    internal object Properties : ReferenceValuePairsPropertyDefinitions<Any, GreaterThan>() {
        override val referenceValuePairs = addReferenceValuePairsDefinition(GreaterThan::referenceValuePairs)
    }

    internal companion object: ReferencePairDataModel<Any, GreaterThan>(
        properties = Properties
    ) {
        override fun invoke(map: Map<Int, *>) = GreaterThan(
            referenceValuePairs = map(0)
        )

        override fun writeJson(obj: GreaterThan, writer: IsJsonLikeWriter, context: DataModelPropertyContext?) {
            writer.writeJsonMapObject(obj.referenceValuePairs, context)
        }
    }
}
