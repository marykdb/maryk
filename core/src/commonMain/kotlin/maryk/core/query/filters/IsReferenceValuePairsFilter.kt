package maryk.core.query.filters

import maryk.core.query.pairs.ReferenceValuePair

/** Filter with reference value pairs */
interface IsReferenceValuePairsFilter<T : Any> : IsReferenceAnyPairsFilter<ReferenceValuePair<T>>
