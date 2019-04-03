package maryk.core.processors.datastore.scanRange

import maryk.core.processors.datastore.matchers.IsIndexPartialToMatch
import maryk.lib.extensions.compare.compareDefinedTo

/**
 * Defines a range to scan. Also contains partial matches to check.
 */
abstract class ScanRange internal constructor(
    val start: ByteArray,
    val startInclusive: Boolean,
    val end: ByteArray? = null,
    val endInclusive: Boolean,
    private val partialMatches: List<IsIndexPartialToMatch>? = null
) {
    /** Checks if [key] is before start of this scan range */
    fun keyBeforeStart(key: ByteArray, offset: Int = 0) =
        start.compareDefinedTo(key, offset).let {
            if (startInclusive) it > 0 else it >= 0
        }

    /** Checks if [key] is after the end of this range start of this scan range */
    open fun keyOutOfRange(key: ByteArray, offset: Int = 0) = end?.let {
        end.compareDefinedTo(key, offset).let {
            if (endInclusive) it < 0 else it <= 0
        }
    } ?: false

    /** Checks if [key] matches the partial matches for this scan range */
    open fun matchesPartials(key: ByteArray, offset: Int = 0): Boolean {
        partialMatches?.let {
            for (partial in partialMatches) {
                if (!partial.match(key, offset)) return false
            }
        }
        return true
    }
}
