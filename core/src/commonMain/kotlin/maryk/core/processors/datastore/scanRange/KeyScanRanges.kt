package maryk.core.processors.datastore.scanRange

import maryk.core.processors.datastore.matchers.IsIndexPartialToMatch
import maryk.core.processors.datastore.matchers.IndexPartialToMatch
import maryk.core.processors.datastore.matchers.UniqueToMatch
import maryk.core.query.pairs.ReferenceValuePair
import maryk.lib.extensions.compare.compareDefinedRange

/** Defines ranges to scan for key. Also contains partial matches to check. */
data class KeyScanRanges internal constructor(
    override val ranges: List<ScanRange>,
    override val partialMatches: List<IsIndexPartialToMatch>? = null,
    val startKey: ByteArray?,
    val includeStart: Boolean,
    val keySize: Int,
    val equalPairs: List<ReferenceValuePair<Any>>,
    val uniques: List<UniqueToMatch>? = null,
    val equalBytes: UInt,
): ScanRanges {
    override fun matchesPartials(key: ByteArray, offset: Int, length: Int, sourceEnd: Int): Boolean {
        if (!super.matchesPartials(key, offset, length, sourceEnd)) {
            return false
        }

        val exactRanges = ranges.filter {
            it.start.isNotEmpty() &&
                it.startInclusive &&
                it.endInclusive &&
                it.end?.contentEquals(it.start) == true
        }
        if (exactRanges.isEmpty()) return true

        return exactRanges.any {
            it.start.size == length || hasExactPrefixExpansion(it.start)
        }
    }

    override fun keyWithinRanges(key: ByteArray, keyIndex: Int): Boolean =
        ranges.any { range ->
            val rangeLength = if (range.isExactInclusiveRange()) keySize else key.size - keyIndex
            !range.keyBeforeStart(key, keyIndex, rangeLength) && !range.keyOutOfRange(key, keyIndex, rangeLength)
        }

    fun keyBeforeStart(key: ByteArray, offset: Int = 0) =
        startKey?.compareDefinedRange(key, offset, keySize)?.let {
            if (includeStart) it > 0 else it >= 0
        } == true

    fun keyAfterStart(key: ByteArray, offset: Int = 0) =
        startKey?.compareDefinedRange(key, offset, keySize)?.let {
            if (includeStart) it < 0 else it <= 0
        } == true

    fun isSingleKey() = ranges.singleOrNull()?.let { range ->
        range.start.size == keySize &&
        range.end?.contentEquals(range.start) == true &&
        range.startInclusive &&
        range.endInclusive
    } == true

    private fun hasExactPrefixExpansion(rangeStart: ByteArray) =
        partialMatches?.any {
            it is IndexPartialToMatch &&
                it.partialMatch &&
                it.toMatch.contentEquals(rangeStart)
        } == true

    private fun ScanRange.isExactInclusiveRange() =
        start.isNotEmpty() &&
            startInclusive &&
            endInclusive &&
            end?.contentEquals(start) == true
}
