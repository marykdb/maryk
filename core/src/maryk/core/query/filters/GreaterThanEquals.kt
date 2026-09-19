package maryk.core.query.filters

import maryk.core.models.ReferenceValuePairsDataModel
import maryk.core.query.pairs.ReferenceValuePair
import maryk.core.values.ObjectValues

/** Referenced values in [referenceValuePairs] should be greater than and equal given value */
data class GreaterThanEquals(
    override val referenceValuePairs: List<ReferenceValuePair<Any>>
) : IsReferenceValuePairsFilter<Any> {
    override val filterType = FilterType.GreaterThanEquals

    @Suppress("UNCHECKED_CAST")
    constructor(vararg referenceValuePair: ReferenceValuePair<*>) : this(referenceValuePair.toList() as List<ReferenceValuePair<Any>>)

    companion object : ReferenceValuePairsDataModel<GreaterThanEquals, Companion, ReferenceValuePair<Any>, Any, Any>(
        pairGetter = GreaterThanEquals::referenceValuePairs,
        pairModel = ReferenceValuePair,
    ) {
        override fun invoke(values: ObjectValues<GreaterThanEquals, Companion>) = GreaterThanEquals(
            referenceValuePairs = values(1u)
        )
    }
}
