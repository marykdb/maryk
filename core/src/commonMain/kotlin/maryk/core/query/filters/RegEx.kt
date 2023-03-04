package maryk.core.query.filters

import maryk.core.models.QueryDataModel
import maryk.core.properties.ReferenceValuePairModel
import maryk.core.query.pairs.ReferenceValueRegexPair
import maryk.core.values.ObjectValues

/** Referenced values in [referenceValuePairs] should match with regular expressions */
data class RegEx internal constructor(
    override val referenceValuePairs: List<ReferenceValueRegexPair>
) : IsReferenceAnyPairsFilter<ReferenceValueRegexPair> {
    override val filterType = FilterType.RegEx

    constructor(vararg referenceValuePair: ReferenceValueRegexPair) : this(referenceValuePair.toList())

    @Suppress("UNCHECKED_CAST")
    companion object : ReferenceValuePairModel<RegEx, Companion, ReferenceValueRegexPair, String, Regex>(
        pairName = "referenceValuePairs",
        pairGetter = RegEx::referenceValuePairs,
        pairModel = ReferenceValueRegexPair as QueryDataModel<ReferenceValueRegexPair, *>,
        pairProperties = ReferenceValueRegexPair.Properties
    ) {
        override fun invoke(values: ObjectValues<RegEx, Companion>) = RegEx(
            referenceValuePairs = values(1u)
        )
    }
}
