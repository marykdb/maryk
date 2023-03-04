package maryk.core.query.filters

import maryk.core.properties.ReferenceValuePairsModel
import maryk.core.query.pairs.ReferenceValueSetPair
import maryk.core.values.ObjectValues

/** Referenced values in [referenceValuePairs] should be within given value set */
data class ValueIn internal constructor(
    override val referenceValuePairs: List<ReferenceValueSetPair<Any>>
) : IsReferenceAnyPairsFilter<ReferenceValueSetPair<Any>> {
    override val filterType = FilterType.ValueIn

    @Suppress("UNCHECKED_CAST")
    constructor(vararg referenceValuePair: ReferenceValueSetPair<*>) : this(referenceValuePair.toList() as List<ReferenceValueSetPair<Any>>)

    companion object : ReferenceValuePairsModel<ValueIn, Companion, ReferenceValueSetPair<*>, Set<Any>, Set<Any>>(
        pairGetter = ValueIn::referenceValuePairs,
        pairModel = ReferenceValueSetPair,
    ) {
        override fun invoke(values: ObjectValues<ValueIn, Companion>) = ValueIn(
            referenceValuePairs = values(1u)
        )
    }
}
