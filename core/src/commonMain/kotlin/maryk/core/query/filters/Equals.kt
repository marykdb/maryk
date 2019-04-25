package maryk.core.query.filters

import maryk.core.models.ReferencePairDataModel
import maryk.core.models.ReferenceValuePairsObjectPropertyDefinitions
import maryk.core.query.pairs.ReferenceValuePair
import maryk.core.values.ObjectValues

/** Referenced values in [referenceValuePairs] should be equal given value */
data class Equals internal constructor(
    override val referenceValuePairs: List<ReferenceValuePair<Any>>
) : IsReferenceValuePairsFilter<Any> {
    override val filterType = FilterType.Equals

    @Suppress("UNCHECKED_CAST")
    constructor(vararg referenceValuePair: ReferenceValuePair<*>) : this(referenceValuePair.toList() as List<ReferenceValuePair<Any>>)

    object Properties : ReferenceValuePairsObjectPropertyDefinitions<Equals, ReferenceValuePair<Any>>(
        pairName = "referenceValuePairs",
        pairGetter = Equals::referenceValuePairs,
        pairModel = ReferenceValuePair
    )

    companion object : ReferencePairDataModel<Equals, Properties, ReferenceValuePair<Any>, Any, Any>(
        properties = Properties,
        pairProperties = ReferenceValuePair.Properties
    ) {
        override fun invoke(values: ObjectValues<Equals, Properties>) = Equals(
            referenceValuePairs = values(1u)
        )
    }
}
