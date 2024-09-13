package maryk.core.processors.datastore.scanRange

import maryk.core.processors.datastore.matchers.IsIndexPartialToMatch

/**
 * Defines ranges to scan. Also contains partial matches to check.
 */
abstract class ScanRanges internal constructor(
    val ranges: List<ScanRange>,
    val partialMatches: List<IsIndexPartialToMatch>? = null
) {
    /** Checks if [key] matches the partial matches for this scan range */
    open fun matchesPartials(key: ByteArray, offset: Int = 0, length: Int = key.size - offset): Boolean =
        partialMatches?.all { it.match(key, offset) } != false

    fun keyWithinRanges(key: ByteArray, keyIndex: Int = 0): Boolean =
        ranges.any { !it.keyBeforeStart(key, keyIndex) && !it.keyOutOfRange(key, keyIndex) }
}
