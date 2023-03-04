package maryk.core.query.filters

import maryk.core.properties.ReferenceValuePairModel
import maryk.core.properties.ReferenceValuePairsModel
import maryk.core.properties.definitions.wrapper.IsDefinitionWrapper
import maryk.core.query.RequestContext
import maryk.core.query.pairs.ReferenceValuePair
import maryk.core.values.ObjectValues

/** Referenced values in [referenceValuePairs] should match with prefixes */
data class Prefix internal constructor(
    override val referenceValuePairs: List<ReferenceValuePair<String>>
) : IsReferenceValuePairsFilter<String> {
    override val filterType = FilterType.Prefix

    constructor(vararg referenceValuePair: ReferenceValuePair<String>) : this(referenceValuePair.toList())

    @Suppress("UNCHECKED_CAST")
    companion object : ReferenceValuePairsModel<Prefix, Companion, ReferenceValuePair<String>, String, String>(
        pairGetter = Prefix::referenceValuePairs,
        pairModel = ReferenceValuePair as ReferenceValuePairModel<ReferenceValuePair<String>, *, *, *, out IsDefinitionWrapper<String, String, RequestContext, ReferenceValuePair<String>>>,
    ) {
        override fun invoke(values: ObjectValues<Prefix, Companion>) = Prefix(
            referenceValuePairs = values(1u)
        )
    }
}
