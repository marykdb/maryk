package maryk.core.query.filters

import maryk.core.models.ReferenceValuePairsDataModel
import maryk.core.query.pairs.ReferenceValuePair
import maryk.core.values.ObjectValues

/** Referenced values in [referenceValuePairs] should be less than and not equal given value */
data class LessThan internal constructor(
    override val referenceValuePairs: List<ReferenceValuePair<Any>>
) : IsReferenceValuePairsFilter<Any> {
    @Suppress("UNCHECKED_CAST")
    constructor(vararg referenceValuePair: ReferenceValuePair<*>) : this(referenceValuePair.toList() as List<ReferenceValuePair<Any>>)

    override val filterType = FilterType.LessThan

    companion object : ReferenceValuePairsDataModel<LessThan, Companion, ReferenceValuePair<Any>, Any, Any>(
        pairGetter = LessThan::referenceValuePairs,
        pairModel = ReferenceValuePair,
    ) {
        override fun invoke(values: ObjectValues<LessThan, Companion>) = LessThan(
            referenceValuePairs = values(1u)
        )
    }
}
