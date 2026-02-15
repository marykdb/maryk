package maryk.core.processors.datastore.matchers

import maryk.core.processors.datastore.matchers.FuzzyMatchResult.MATCH
import maryk.core.processors.datastore.matchers.FuzzyMatchResult.NO_MATCH
import maryk.core.processors.datastore.matchers.FuzzyMatchResult.OUT_OF_RANGE
import kotlin.test.Test
import kotlin.test.expect

class QualifierMatcherTest {
    @Test
    fun exactMatch() {
        val qualifierMatcher = QualifierExactMatcher(
            reference = null,
            ("BBBB").hexToByteArray()
        )
        expect(0) { qualifierMatcher.compareTo("BBBB".hexToByteArray(), 0) }
        expect(0) { qualifierMatcher.compareTo("0000BBBB".hexToByteArray(), 2) }
        expect(17) { qualifierMatcher.compareTo("0000AAAA".hexToByteArray(), 2) }
        expect(-17) { qualifierMatcher.compareTo("0000CCCC".hexToByteArray(), 2) }
    }

    @Test
    fun fuzzyMatch() {
        val qualifierMatcher = QualifierFuzzyMatcher(
            reference = null,
            listOf(
                "bbbb".hexToByteArray(),
                "cccc".hexToByteArray()
            ),
            listOf(
                FuzzyDynamicLengthMatch,
                FuzzyExactLengthMatch(4)
            )
        )
        expect("bbbb") { qualifierMatcher.firstPossible().toHexString() }
        expect(MATCH) { qualifierMatcher.isMatch("00bbbb02ffffccccdddddddd".hexToByteArray(), 1) }
        expect(NO_MATCH) { qualifierMatcher.isMatch("00bbbb02ffffccccdddd".hexToByteArray(), 1) }
        expect(NO_MATCH) { qualifierMatcher.isMatch("00bbbb02ffffaaaadddddddd".hexToByteArray(), 1) }
        expect(OUT_OF_RANGE) { qualifierMatcher.isMatch("00cccc02ffffccccdddddddd".hexToByteArray(), 1) }
    }
}
