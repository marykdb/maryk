package maryk.core.query.filters

import maryk.core.properties.ReferenceValuePairModel
import maryk.core.query.pairs.ReferenceValuePair
import maryk.core.values.ObjectValues

/** Referenced values in [referenceValuePairs] should be equal given value */
data class Equals internal constructor(
    override val referenceValuePairs: List<ReferenceValuePair<Any>>
) : IsReferenceValuePairsFilter<Any> {
    override val filterType = FilterType.Equals

    @Suppress("UNCHECKED_CAST")
    constructor(vararg referenceValuePair: ReferenceValuePair<*>) : this(referenceValuePair.toList() as List<ReferenceValuePair<Any>>)

    companion object : ReferenceValuePairModel<Equals, Companion, ReferenceValuePair<Any>, Any, Any>(
        pairName = "referenceValuePairs",
        pairGetter = Equals::referenceValuePairs,
        pairModel = ReferenceValuePair,
        pairProperties = ReferenceValuePair.Properties,
    ) {
        override fun invoke(values: ObjectValues<Equals, Companion>) = Equals(
            referenceValuePairs = values(1u)
        )
    }
}
