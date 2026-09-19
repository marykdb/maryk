package maryk.core.processors.datastore.scanRange

import maryk.core.processors.datastore.matchers.IndexPartialToMatch
import maryk.core.processors.datastore.matchers.IndexPartialToBeOneOf
import maryk.core.processors.datastore.matchers.IndexPartialSizeToMatch
import maryk.core.processors.datastore.matchers.IsIndexPartialToMatch
import maryk.lib.extensions.compare.matchesRangePart

/**
 * Defines ranges to scan on Indexables. Also contains partial matches to check.
 */
data class IndexableScanRanges internal constructor(
    override val ranges: List<ScanRange>,
    override val partialMatches: List<IsIndexPartialToMatch>? = null,
    val keyScanRange: KeyScanRanges,
    val valueMatches: List<IndexValueMatch> = emptyList()
): ScanRanges {
    override fun matchesPartials(key: ByteArray, offset: Int, length: Int, sourceEnd: Int): Boolean {
        val keyIndex = sourceEnd - keyScanRange.keySize
        if (keyIndex < offset) {
            return false
        }

        if (!keyScanRange.matchesPartials(key, keyIndex, keyScanRange.keySize, sourceEnd) ||
            !super.matchesPartials(key, offset, length, sourceEnd)
        ) {
            return false
        }

        val exactPrefixRanges = ranges.filter {
            it.start.isNotEmpty() &&
                it.startInclusive &&
                it.endInclusive &&
                it.end?.contentEquals(it.start) == true &&
                key.matchesRangePart(offset, it.start, sourceLength = length, length = it.start.size)
        }
        if (exactPrefixRanges.isEmpty()) return true

        return exactPrefixRanges.any {
            it.start.size == length ||
                hasExactPrefixExpansion(it.start) ||
                hasAnchoredExactPartSize(it.start.size)
        }
    }

    private fun hasExactPrefixExpansion(rangeStart: ByteArray) =
        partialMatches?.any {
            (it is IndexPartialToMatch &&
                it.partialMatch &&
                it.toMatch.contentEquals(rangeStart)) ||
                (it is IndexPartialToBeOneOf &&
                    it.partialMatch &&
                    it.toBeOneOf.any(rangeStart::contentEquals))
        } == true

    private fun hasAnchoredExactPartSize(rangeStartSize: Int) =
        partialMatches?.any {
            it is IndexPartialSizeToMatch && it.size == rangeStartSize
        } == true
}
