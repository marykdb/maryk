package maryk.core.query.filters

import maryk.core.properties.ReferenceValuePairsModel
import maryk.core.query.ValueRange
import maryk.core.query.pairs.ReferenceValueRangePair
import maryk.core.values.ObjectValues

/**
 * Compares [referenceValuePairs] if referred values are within given ranges.
 */
data class Range internal constructor(
    override val referenceValuePairs: List<ReferenceValueRangePair<*>>
) : IsReferenceAnyPairsFilter<ReferenceValueRangePair<*>> {
    override val filterType = FilterType.Range

    constructor(vararg range: ReferenceValueRangePair<*>) : this(range.toList())

    companion object : ReferenceValuePairsModel<Range, Companion, ReferenceValueRangePair<*>, ValueRange<*>, ValueRange<*>>(
        pairGetter = Range::referenceValuePairs,
        pairModel = ReferenceValueRangePair,
    ) {
        override fun invoke(values: ObjectValues<Range, Companion>) = Range(
            referenceValuePairs = values(1u)
        )
    }
}
