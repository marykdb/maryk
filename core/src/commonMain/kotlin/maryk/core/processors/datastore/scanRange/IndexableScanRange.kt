package maryk.core.processors.datastore.scanRange

import maryk.core.processors.datastore.matchers.IsIndexPartialToMatch

/**
 * Defines a range to scan on Indexables. Also contains partial matches to check.
 */
class IndexableScanRange internal constructor(
    start: ByteArray,
    startInclusive: Boolean,
    end: ByteArray? = null,
    endInclusive: Boolean,
    partialMatches: List<IsIndexPartialToMatch>? = null,
    val keyScanRange: KeyScanRange
): ScanRange(start, startInclusive, end, endInclusive, partialMatches) {
    override fun matchesPartials(key: ByteArray, offset: Int): Boolean {
        val keyIndex = key.size - keyScanRange.keySize

        return when {
            // If key or parts do not match, skip
            keyScanRange.keyBeforeStart(key, keyIndex) ||
                keyScanRange.keyOutOfRange(key, keyIndex) ||
                !keyScanRange.matchesPartials(key, keyIndex) ->
                false
            else -> super.matchesPartials(key, offset)
        }
    }
}
