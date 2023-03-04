package maryk.core.query.filters

import maryk.core.properties.ReferenceValuePairsModel
import maryk.core.query.pairs.ReferenceValuePair
import maryk.core.values.ObjectValues

/** Referenced values in [referenceValuePairs] should be less than and not equal given value */
data class LessThanEquals internal constructor(
    override val referenceValuePairs: List<ReferenceValuePair<Any>>
) : IsReferenceValuePairsFilter<Any> {
    override val filterType = FilterType.LessThanEquals

    @Suppress("UNCHECKED_CAST")
    constructor(vararg referenceValuePair: ReferenceValuePair<out Any>) : this(referenceValuePair.toList() as List<ReferenceValuePair<Any>>)

    companion object : ReferenceValuePairsModel<LessThanEquals, Companion, ReferenceValuePair<Any>, Any, Any>(
        pairGetter = LessThanEquals::referenceValuePairs,
        pairModel = ReferenceValuePair,
    ) {
        override fun invoke(values: ObjectValues<LessThanEquals, Companion>) = LessThanEquals(
            referenceValuePairs = values(1u)
        )
    }
}
