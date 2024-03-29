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
        startKey?.let { it.compareDefinedTo(key, offset, keySize) > 0 } ?: false

    fun keyAfterStart(key: ByteArray, offset: Int = 0) =
        startKey?.let { it.compareDefinedTo(key, offset, keySize) < 0 } ?: false

    fun isSingleKey(): Boolean {
        return if (ranges.size == 1) {
            val firstRange = ranges.first()
            firstRange.start.size == keySize && firstRange.end?.contentEquals(firstRange.start) == true && firstRange.startInclusive && firstRange.endInclusive
        } else {
            false
        }
    }
}
