package maryk.core.query.filters

import maryk.core.models.QueryDataModel
import maryk.core.properties.ReferenceValuePairModel
import maryk.core.query.pairs.ReferenceValueSetPair
import maryk.core.values.ObjectValues

/** Referenced values in [referenceValuePairs] should be within given value set */
data class ValueIn internal constructor(
    override val referenceValuePairs: List<ReferenceValueSetPair<Any>>
) : IsReferenceAnyPairsFilter<ReferenceValueSetPair<Any>> {
    override val filterType = FilterType.ValueIn

    @Suppress("UNCHECKED_CAST")
    constructor(vararg referenceValuePair: ReferenceValueSetPair<*>) : this(referenceValuePair.toList() as List<ReferenceValueSetPair<Any>>)

    @Suppress("UNCHECKED_CAST")
    companion object : ReferenceValuePairModel<ValueIn, Companion, ReferenceValueSetPair<*>, Set<Any>, Set<Any>>(
        pairName = "referenceValuePairs",
        pairGetter = ValueIn::referenceValuePairs,
        pairModel = ReferenceValueSetPair as QueryDataModel<ReferenceValueSetPair<*>, *>,
        pairProperties = ReferenceValueSetPair.Properties
    ) {
        override fun invoke(values: ObjectValues<ValueIn, Companion>) = ValueIn(
            referenceValuePairs = values(1u)
        )
    }
}
