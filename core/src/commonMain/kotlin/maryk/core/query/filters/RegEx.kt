package maryk.core.query.filters

import maryk.core.models.QueryDataModel
import maryk.core.models.ReferencePairDataModel
import maryk.core.models.ReferenceValuePairsObjectPropertyDefinitions
import maryk.core.query.pairs.ReferenceValueRegexPair
import maryk.core.values.ObjectValues

/** Referenced values in [referenceValuePairs] should match with regular expressions */
data class RegEx internal constructor(
    val referenceValuePairs: List<ReferenceValueRegexPair>
) : IsFilter {
    override val filterType = FilterType.RegEx

    constructor(vararg referenceValuePair: ReferenceValueRegexPair) : this(referenceValuePair.toList())

    @Suppress("UNCHECKED_CAST")
    object Properties : ReferenceValuePairsObjectPropertyDefinitions<RegEx, ReferenceValueRegexPair>(
        pairName = "referenceValuePairs",
        pairGetter = RegEx::referenceValuePairs,
        pairModel = ReferenceValueRegexPair as QueryDataModel<ReferenceValueRegexPair, *>
    )

    companion object : ReferencePairDataModel<RegEx, Properties, ReferenceValueRegexPair, Regex>(
        properties = Properties,
        pairProperties = ReferenceValueRegexPair.Properties
    ) {
        override fun invoke(values: ObjectValues<RegEx, Properties>) = RegEx(
            referenceValuePairs = values(1)
        )
    }
}
