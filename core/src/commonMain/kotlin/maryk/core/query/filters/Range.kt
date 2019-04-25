package maryk.core.query.filters

import maryk.core.models.QueryDataModel
import maryk.core.models.ReferencePairDataModel
import maryk.core.models.ReferenceValuePairsObjectPropertyDefinitions
import maryk.core.query.ValueRange
import maryk.core.query.pairs.ReferenceValueRangePair
import maryk.core.values.ObjectValues

/**
 * Compares [referenceRangePairs] if referred values are within given ranges.
 */
data class Range internal constructor(
    val referenceRangePairs: List<ReferenceValueRangePair<*>>
) : IsFilter {
    override val filterType = FilterType.Range

    constructor(vararg range: ReferenceValueRangePair<*>) : this(range.toList())

    @Suppress("UNCHECKED_CAST")
    object Properties : ReferenceValuePairsObjectPropertyDefinitions<Range, ReferenceValueRangePair<*>>(
        pairName = "referenceValuePairs",
        pairGetter = Range::referenceRangePairs,
        pairModel = ReferenceValueRangePair as QueryDataModel<ReferenceValueRangePair<*>, *>
    )

    companion object : ReferencePairDataModel<Range, Properties, ReferenceValueRangePair<*>, ValueRange<*>, ValueRange<*>>(
        properties = Properties,
        pairProperties = ReferenceValueRangePair.Properties
    ) {
        override fun invoke(values: ObjectValues<Range, Properties>) = Range(
            referenceRangePairs = values(1u)
        )
    }
}
