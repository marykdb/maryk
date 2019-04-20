package maryk.core.query.filters

import maryk.core.models.QueryDataModel
import maryk.core.models.ReferencePairDataModel
import maryk.core.models.ReferenceValuePairsObjectPropertyDefinitions
import maryk.core.query.pairs.ReferenceValuePair
import maryk.core.query.pairs.ReferenceValuePairPropertyDefinitions
import maryk.core.values.ObjectValues

/** Referenced values in [referenceValuePairs] should match with prefixes */
data class Prefix internal constructor(
    override val referenceValuePairs: List<ReferenceValuePair<String>>
) : IsReferenceValuePairsFilter<String> {
    override val filterType = FilterType.Prefix

    constructor(vararg referenceValuePair: ReferenceValuePair<String>) : this(referenceValuePair.toList())

    @Suppress("UNCHECKED_CAST")
    object Properties : ReferenceValuePairsObjectPropertyDefinitions<Prefix, ReferenceValuePair<String>>(
        pairName = "referenceValuePairs",
        pairGetter = Prefix::referenceValuePairs,
        pairModel = ReferenceValuePair as QueryDataModel<ReferenceValuePair<String>, *>
    )

    @Suppress("UNCHECKED_CAST")
    companion object : ReferencePairDataModel<Prefix, Properties, ReferenceValuePair<String>, String>(
        properties = Properties,
        pairProperties = ReferenceValuePair.Properties as ReferenceValuePairPropertyDefinitions<ReferenceValuePair<String>, String>
    ) {
        override fun invoke(values: ObjectValues<Prefix, Properties>) = Prefix(
            referenceValuePairs = values(1u)
        )
    }
}
