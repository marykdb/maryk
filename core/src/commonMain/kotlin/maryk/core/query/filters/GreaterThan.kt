package maryk.core.query.filters

import maryk.core.models.ReferencePairDataModel
import maryk.core.models.ReferenceValuePairsObjectPropertyDefinitions
import maryk.core.query.pairs.ReferenceValuePair
import maryk.core.values.ObjectValues

/** Referenced values in [referenceValuePairs] should be greater than and not equal given value */
data class GreaterThan internal constructor(
    override val referenceValuePairs: List<ReferenceValuePair<Any>>
) : IsReferenceValuePairsFilter<Any> {
    override val filterType = FilterType.GreaterThan

    @Suppress("UNCHECKED_CAST")
    constructor(vararg referenceValuePair: ReferenceValuePair<*>) : this(referenceValuePair.toList() as List<ReferenceValuePair<Any>>)

    object Properties : ReferenceValuePairsObjectPropertyDefinitions<GreaterThan, ReferenceValuePair<Any>>(
        pairName = "referenceValuePairs",
        pairGetter = GreaterThan::referenceValuePairs,
        pairModel = ReferenceValuePair
    )

    companion object : ReferencePairDataModel<GreaterThan, Properties, ReferenceValuePair<Any>, Any, Any>(
        properties = Properties,
        pairProperties = ReferenceValuePair.Properties
    ) {
        override fun invoke(values: ObjectValues<GreaterThan, Properties>) = GreaterThan(
            referenceValuePairs = values(1u)
        )
    }
}
