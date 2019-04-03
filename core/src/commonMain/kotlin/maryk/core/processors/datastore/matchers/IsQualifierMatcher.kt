package maryk.core.processors.datastore.matchers

import maryk.core.processors.datastore.matchers.FuzzyMatchResult.MATCH
import maryk.core.processors.datastore.matchers.FuzzyMatchResult.NO_MATCH
import maryk.core.processors.datastore.matchers.FuzzyMatchResult.OUT_OF_RANGE
import maryk.lib.extensions.compare.compareWithOffsetTo

/** Defines a matcher for a qualifier. */
sealed class IsQualifierMatcher

/** Defines an exact [qualifier] matcher */
class QualifierExactMatcher(
    private val qualifier: ByteArray
) : IsQualifierMatcher() {
    fun compareTo(qualifier: ByteArray, offset: Int): Int {
        return this.qualifier.compareWithOffsetTo(qualifier, offset)
    }
}

enum class FuzzyMatchResult {
    NO_MATCH, MATCH, OUT_OF_RANGE
}

/** Defines a fuzzy qualifier matcher with [qualifierParts] and in between [fuzzyMatches] */
class QualifierFuzzyMatcher(
    private val qualifierParts: Array<ByteArray>,
    private val fuzzyMatches: Array<IsFuzzyMatch>
) : IsQualifierMatcher() {
    /** Find first possible match */
    fun firstPossible() = qualifierParts.first()

    /** Compare current [qualifier] at [offset] */
    fun isMatch(qualifier: ByteArray, offset: Int) : FuzzyMatchResult {
        var index = offset

        for ((qIndex, qPart) in qualifierParts.withIndex()) {
            for (byte in qPart) {
                if (byte != qualifier[index++]) {
                    // If first part does not match it is out of range. Otherwise possible matches so no match
                    return if (qIndex == 0) OUT_OF_RANGE else NO_MATCH
                }
            }

            if (fuzzyMatches.lastIndex >= qIndex) {
                try {
                    fuzzyMatches[qIndex].skip {
                        qualifier[index++]
                    }
                } catch (e: Throwable) {
                    return NO_MATCH
                }
            }
        }

        return MATCH
    }
}
