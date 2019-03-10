package maryk.core.processors.datastore

import maryk.lib.extensions.compare.compareDefinedTo

/**
 * Defines a range to scan on Indexables. Also contains partial matches to check.
 */
class IndexableScanRange internal constructor(
    start: ByteArray,
    startInclusive: Boolean,
    end: ByteArray? = null,
    endInclusive: Boolean,
    partialMatches: List<IsIndexPartialToMatch>? = null
): ScanRange(start, startInclusive, end, endInclusive, partialMatches) {
    override fun keyOutOfRange(key: ByteArray) = end?.let {
        end.compareDefinedTo(key).let {
            if (endInclusive) it <= 0 else it < 0
        }
    } ?: false
}
