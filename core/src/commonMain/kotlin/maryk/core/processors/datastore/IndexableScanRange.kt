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
    partialMatches: List<IsIndexPartialToMatch>? = null,
    val keyScanRange: KeyScanRange
): ScanRange(start, startInclusive, end, endInclusive, partialMatches) {
    override fun keyOutOfRange(key: ByteArray, offset: Int) = end?.let {
        end.compareDefinedTo(key, offset).let {
            if (endInclusive) it < 0 else it <= 0
        }
    } ?: false

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
