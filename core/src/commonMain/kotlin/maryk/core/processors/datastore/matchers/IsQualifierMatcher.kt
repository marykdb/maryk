package maryk.core.processors.datastore.matchers

import maryk.core.processors.datastore.matchers.FuzzyMatchResult.MATCH
import maryk.core.processors.datastore.matchers.FuzzyMatchResult.NO_MATCH
import maryk.core.processors.datastore.matchers.FuzzyMatchResult.OUT_OF_RANGE
import maryk.lib.extensions.compare.compareToWithOffsetLength

/** Defines a matcher for a qualifier. */
sealed class IsQualifierMatcher

/** Defines an exact [qualifier] matcher */
class QualifierExactMatcher(
    val qualifier: ByteArray
) : IsQualifierMatcher() {
    fun compareTo(qualifier: ByteArray, offset: Int): Int {
        return this.qualifier.compareToWithOffsetLength(qualifier, offset)
    }
}

enum class FuzzyMatchResult {
    NO_MATCH, MATCH, OUT_OF_RANGE
}

/** Defines a fuzzy qualifier matcher with [qualifierParts] and in between [fuzzyMatchers] */
class QualifierFuzzyMatcher(
    val qualifierParts: List<ByteArray>,
    val fuzzyMatchers: List<IsFuzzyMatcher>
) : IsQualifierMatcher() {
    /** Find first possible match */
    fun firstPossible() = qualifierParts.first()

    /** Compare current [qualifier] at [offset] */
    fun isMatch(qualifier: ByteArray, offset: Int, length: Int = qualifier.size) : FuzzyMatchResult {
        var index = offset
        val lastIndexPlusOne = offset + length

        for ((qIndex, qPart) in qualifierParts.withIndex()) {
            for (byte in qPart) {
                if (lastIndexPlusOne <= index) { return NO_MATCH }
                if (byte != qualifier[index++]) {
                    // If first part does not match it is out of range. Otherwise possible matches so no match
                    return if (qIndex == 0) OUT_OF_RANGE else NO_MATCH
                }
            }

            if (fuzzyMatchers.lastIndex >= qIndex) {
                try {
                    fuzzyMatchers[qIndex].skip {
                        if (index >= lastIndexPlusOne) {
                            // So JS skips out.
                            throw Throwable("0 char encountered")
                        }

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
