package maryk.core.query.filters

import maryk.core.properties.ReferenceValuePairsModel
import maryk.core.query.pairs.ReferenceValueRegexPair
import maryk.core.values.ObjectValues

/** Referenced values in [referenceValuePairs] should match with regular expressions */
data class RegEx internal constructor(
    override val referenceValuePairs: List<ReferenceValueRegexPair>
) : IsReferenceAnyPairsFilter<ReferenceValueRegexPair> {
    override val filterType = FilterType.RegEx

    constructor(vararg referenceValuePair: ReferenceValueRegexPair) : this(referenceValuePair.toList())

    companion object : ReferenceValuePairsModel<RegEx, Companion, ReferenceValueRegexPair, String, Regex>(
        pairGetter = RegEx::referenceValuePairs,
        pairModel = ReferenceValueRegexPair,
    ) {
        override fun invoke(values: ObjectValues<RegEx, Companion>) = RegEx(
            referenceValuePairs = values(1u)
        )
    }
}
