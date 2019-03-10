package maryk.core.processors.datastore

import maryk.core.query.pairs.ReferenceValuePair

/** Defines a range to scan for key. Also contains partial matches to check. */
class KeyScanRange internal constructor(
    start: ByteArray,
    startInclusive: Boolean,
    end: ByteArray? = null,
    endInclusive: Boolean,
    partialMatches: List<IsIndexPartialToMatch>? = null,
    val keySize: Int,
    val equalPairs: List<ReferenceValuePair<Any>>,
    val uniques: List<UniqueToMatch>? = null
): ScanRange(start, startInclusive, end, endInclusive, partialMatches)
