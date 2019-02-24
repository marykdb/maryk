package maryk.core.query.filters

import maryk.core.query.pairs.ReferenceValuePair

/** Filter */
interface IsReferenceValuePairsFilter<T : Any> : IsFilter {
    val referenceValuePairs: List<ReferenceValuePair<T>>
}
