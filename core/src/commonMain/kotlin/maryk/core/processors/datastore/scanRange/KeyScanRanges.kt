package maryk.core.processors.datastore.scanRange

import maryk.core.processors.datastore.matchers.IsIndexPartialToMatch
import maryk.core.processors.datastore.matchers.UniqueToMatch
import maryk.core.query.pairs.ReferenceValuePair
import maryk.lib.extensions.compare.compareDefinedTo

/** Defines ranges to scan for key. Also contains partial matches to check. */
class KeyScanRanges internal constructor(
    ranges: List<ScanRange>,
    partialMatches: List<IsIndexPartialToMatch>? = null,
    val startKey: ByteArray?,
    val includeStart: Boolean,
    val keySize: Int,
    val equalPairs: List<ReferenceValuePair<Any>>,
    val uniques: List<UniqueToMatch>? = null
): ScanRanges(ranges, partialMatches) {
    fun keyBeforeStart(key: ByteArray, offset: Int = 0) =
        startKey?.compareDefinedTo(key, offset, keySize)?.let { it > 0 } == true

    fun keyAfterStart(key: ByteArray, offset: Int = 0) =
        startKey?.compareDefinedTo(key, offset, keySize)?.let { it < 0 } == true

    fun isSingleKey() = ranges.singleOrNull()?.let { range ->
        range.start.size == keySize &&
        range.end?.contentEquals(range.start) == true &&
        range.startInclusive &&
        range.endInclusive
    } == true
}
