package maryk.core.query.filters

import maryk.core.models.ReferencePairDataModel
import maryk.core.models.ReferenceValuePairsObjectPropertyDefinitions
import maryk.core.query.pairs.ReferenceValuePair
import maryk.core.values.ObjectValues

/** Referenced values in [referenceValuePairs] should be less than and not equal given value */
data class LessThan internal constructor(
    override val referenceValuePairs: List<ReferenceValuePair<Any>>
) : IsReferenceValuePairsFilter<Any> {
    @Suppress("UNCHECKED_CAST")
    constructor(vararg referenceValuePair: ReferenceValuePair<*>) : this(referenceValuePair.toList() as List<ReferenceValuePair<Any>>)

    override val filterType = FilterType.LessThan

    object Properties : ReferenceValuePairsObjectPropertyDefinitions<LessThan, ReferenceValuePair<Any>>(
        pairName = "referenceValuePairs",
        pairGetter = LessThan::referenceValuePairs,
        pairModel = ReferenceValuePair
    )

    companion object : ReferencePairDataModel<LessThan, Properties, ReferenceValuePair<Any>, Any, Any>(
        properties = Properties,
        pairProperties = ReferenceValuePair.Properties
    ) {
        override fun invoke(values: ObjectValues<LessThan, Properties>) = LessThan(
            referenceValuePairs = values(1u)
        )
    }
}
