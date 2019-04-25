package maryk.core.query.filters

import maryk.core.models.QueryDataModel
import maryk.core.models.ReferencePairDataModel
import maryk.core.models.ReferenceValuePairsObjectPropertyDefinitions
import maryk.core.query.pairs.ReferenceValueSetPair
import maryk.core.values.ObjectValues

/** Referenced values in [referenceValuePairs] should be within given value set */
data class ValueIn internal constructor(
    val referenceValuePairs: List<ReferenceValueSetPair<Any>>
) : IsFilter {
    override val filterType = FilterType.ValueIn

    @Suppress("UNCHECKED_CAST")
    constructor(vararg referenceValuePair: ReferenceValueSetPair<*>) : this(referenceValuePair.toList() as List<ReferenceValueSetPair<Any>>)

    @Suppress("UNCHECKED_CAST")
    object Properties : ReferenceValuePairsObjectPropertyDefinitions<ValueIn, ReferenceValueSetPair<*>>(
        pairName = "referenceValuePairs",
        pairGetter = ValueIn::referenceValuePairs,
        pairModel = ReferenceValueSetPair as QueryDataModel<ReferenceValueSetPair<*>, *>
    )

    companion object : ReferencePairDataModel<ValueIn, Properties, ReferenceValueSetPair<*>, Set<*>, Set<*>>(
        properties = Properties,
        pairProperties = ReferenceValueSetPair.Properties
    ) {
        override fun invoke(values: ObjectValues<ValueIn, Properties>) = ValueIn(
            referenceValuePairs = values(1u)
        )
    }
}
