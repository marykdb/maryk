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
                hasAnchoredExactPrefix(it.start, key, offset, sourceEnd)
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

    private fun hasAnchoredExactPrefix(
        rangeStart: ByteArray,
        row: ByteArray? = null,
        sourceStart: Int = 0,
        sourceEnd: Int = row?.size ?: 0,
    ): Boolean {
        val sizes = partialMatches.orEmpty().filterIsInstance<IndexPartialSizeToMatch>()
            .associateBy { it.indexableIndex }
        val alternatives = partialMatches.orEmpty().filterIsInstance<IndexPartialToBeOneOf>()
            .filterNot { it.partialMatch }
            .associateBy { it.indexableIndex }
        val indices = (sizes.keys + alternatives.keys).sorted()
        var offsets = setOf(0)
        for ((expectedIndex, index) in indices.withIndex()) {
            // Membership after a gap is a residual filter, not part of the
            // leading prefix. Still validate every contiguous anchored part
            // before accepting a byte sequence that could match two layouts.
            if (index != expectedIndex) return rangeStart.size in offsets
            val size = sizes[index]?.size
            val nextOffsets = buildSet {
                for (offset in offsets) {
                    if (size != null) {
                        if (size <= rangeStart.size - offset) add(offset + size)
                    } else {
                        val alternative = alternatives.getValue(index)
                        val actualSize = row?.let { alternative.getByteIndexAndSize(it, sourceStart, sourceEnd)?.second }
                        alternative.toBeOneOf.forEach { value ->
                            if ((actualSize == null || value.size == actualSize) &&
                                rangeStart.matchesRangePart(offset, value, sourceLength = rangeStart.size - offset)
                            ) {
                                add(offset + value.size)
                            }
                        }
                    }
                }
            }
            if (nextOffsets.isEmpty()) return false
            offsets = nextOffsets
        }
        return rangeStart.size in offsets
    }

    /** Compare an anchored equality prefix over its complete parts, without the remaining index values. */
    fun rangeLength(range: ScanRange, valueSize: Int): Int =
        if (
            range.start.isNotEmpty() && range.startInclusive && range.endInclusive &&
            range.end?.contentEquals(range.start) == true &&
            (hasExactPrefixExpansion(range.start) || hasAnchoredExactPrefix(range.start))
        ) range.start.size else valueSize
}
