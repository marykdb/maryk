package maryk.core.processors.datastore.matchers

import maryk.core.processors.datastore.matchers.FuzzyMatchResult.MATCH
import maryk.core.processors.datastore.matchers.FuzzyMatchResult.NO_MATCH
import maryk.core.processors.datastore.matchers.FuzzyMatchResult.OUT_OF_RANGE
import maryk.core.properties.references.IsPropertyReference
import maryk.lib.extensions.compare.compareToRange

/** Defines a matcher for a qualifier. */
sealed class IsQualifierMatcher

/**
 * Defines an exact [qualifier] matcher.
 * Optionally set [referencedQualifierMatcher] to match values behind a reference
 */
class QualifierExactMatcher(
    val reference: IsPropertyReference<*, *, *>?,
    val qualifier: ByteArray,
    val referencedQualifierMatcher: ReferencedQualifierMatcher? = null
) : IsQualifierMatcher() {
    fun compareTo(qualifier: ByteArray, offset: Int) =
        this.qualifier.compareToRange(qualifier, offset)
}

enum class FuzzyMatchResult {
    NO_MATCH, MATCH, OUT_OF_RANGE
}

/**
 * Defines a fuzzy qualifier matcher with [qualifierParts] and in between [fuzzyMatchers]
 * Optionally set [referencedQualifierMatcher] to match values behind a reference
 */
class QualifierFuzzyMatcher(
    val reference: IsPropertyReference<*, *, *>?,
    val qualifierParts: List<ByteArray>,
    val fuzzyMatchers: List<IsFuzzyMatcher>,
    val referencedQualifierMatcher: ReferencedQualifierMatcher? = null
) : IsQualifierMatcher() {
    /** Find first possible match */
    fun firstPossible() = qualifierParts.first()

    /** Compare current [qualifier] at [offset] */
    fun isMatch(
        qualifier: ByteArray,
        offset: Int,
        length: Int = qualifier.size - offset
    ): FuzzyMatchResult {
        var index = offset
        // Clamp end to the physical array end
        val endExclusive = (offset + length).coerceAtMost(qualifier.size)

        qualifierParts.forEachIndexed { qIndex, qPart ->
            if (!qPart.all { byte ->
                if (index >= endExclusive) return NO_MATCH
                qualifier[index++] == byte
            }) {
                return if (qIndex == 0) OUT_OF_RANGE else NO_MATCH
            }

            if (qIndex <= fuzzyMatchers.lastIndex) {
                try {
                    fuzzyMatchers[qIndex].skip {
                        if (index >= endExclusive) {
                            // Force controlled exit for JS; Wasm would trap otherwise
                            throw IndexOutOfBoundsException()
                        }
                        qualifier[index++]
                    }
                } catch (_: IndexOutOfBoundsException) {
                    return NO_MATCH
                }
            }
        }

        return MATCH
    }
}
