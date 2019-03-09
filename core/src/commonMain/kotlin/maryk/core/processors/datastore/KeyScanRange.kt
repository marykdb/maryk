package maryk.core.processors.datastore

import maryk.core.query.pairs.ReferenceValuePair
import maryk.lib.extensions.compare.compareTo

/** Defines a range to scan for key. Also contains partial matches to check. */
class KeyScanRange internal constructor(
    start: ByteArray,
    val startInclusive: Boolean,
    end: ByteArray? = null,
    val endInclusive: Boolean,
    val equalPairs: List<ReferenceValuePair<Any>>,
    val uniques: List<UniqueToMatch>? = null,
    partialMatches: List<IsIndexPartialToMatch>? = null
): ScanRange(start, end, partialMatches) {
    override fun keyBeforeStart(key: ByteArray) = if (startInclusive) key < start else key <= start
    override fun keyOutOfRange(key: ByteArray) = end?.let { if (endInclusive) end < key else end <= key } ?: false
}
