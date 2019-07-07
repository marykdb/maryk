package maryk.core.processors.datastore.scanRange

import maryk.core.processors.datastore.matchers.IsIndexPartialToMatch

/**
 * Defines ranges to scan on Indexables. Also contains partial matches to check.
 */
class IndexableScanRanges internal constructor(
    ranges: List<ScanRange>,
    partialMatches: List<IsIndexPartialToMatch>? = null,
    val keyScanRange: KeyScanRanges
): ScanRanges(ranges, partialMatches) {
    override fun matchesPartials(key: ByteArray, offset: Int, length: Int): Boolean {
        val keyIndex = key.size - keyScanRange.keySize

        return when {
            // If key or parts do not match, skip
            !keyScanRange.keyWithinRanges(key, keyIndex) || !keyScanRange.matchesPartials(key, keyIndex) ->
                false
            else -> super.matchesPartials(key, offset, length)
        }
    }
}
