package maryk.core.processors.datastore.scanRange

import maryk.core.processors.datastore.matchers.IsIndexPartialToMatch

/**
 * Defines ranges to scan. Also contains partial matches to check.
 */
abstract class ScanRanges internal constructor(
    val ranges: List<ScanRange>,
    private val partialMatches: List<IsIndexPartialToMatch>? = null
) {
    /** Checks if [key] matches the partial matches for this scan range */
    open fun matchesPartials(key: ByteArray, offset: Int = 0): Boolean {
        partialMatches?.let {
            for (partial in partialMatches) {
                if (!partial.match(key, offset)) return false
            }
        }
        return true
    }

    fun keyWithinRanges(key: ByteArray, keyIndex: Int): Boolean {
        for (range in ranges) {
            if (!range.keyBeforeStart(key, keyIndex) && !range.keyOutOfRange(key, keyIndex)) {
                return true
            }
        }

        return false
    }
}
