package maryk.core.processors.datastore.matchers

import maryk.core.processors.datastore.matchers.FuzzyMatchResult.MATCH
import maryk.core.processors.datastore.matchers.FuzzyMatchResult.NO_MATCH
import maryk.core.processors.datastore.matchers.FuzzyMatchResult.OUT_OF_RANGE
import maryk.lib.extensions.initByteArrayByHex
import maryk.lib.extensions.toHex
import kotlin.test.Test
import kotlin.test.expect

class QualifierMatcherTest {
    @Test
    fun exactMatch() {
        val qualifierMatcher = QualifierExactMatcher(
            initByteArrayByHex("BBBB")
        )
        expect(0) { qualifierMatcher.compareTo(initByteArrayByHex("BBBB"), 0) }
        expect(0) { qualifierMatcher.compareTo(initByteArrayByHex("0000BBBB"), 2) }
        expect(17) { qualifierMatcher.compareTo(initByteArrayByHex("0000AAAA"), 2) }
        expect(-17) { qualifierMatcher.compareTo(initByteArrayByHex("0000CCCC"), 2) }
    }

    @Test
    fun fuzzyMatch() {
        val qualifierMatcher = QualifierFuzzyMatcher(
            listOf(
                initByteArrayByHex("bbbb"),
                initByteArrayByHex("cccc")
            ),
            listOf(
                FuzzyDynamicLengthMatch,
                FuzzyExactLengthMatch(4)
            )
        )
        expect("bbbb") { qualifierMatcher.firstPossible().toHex() }
        expect(MATCH) { qualifierMatcher.isMatch(initByteArrayByHex("00bbbb02ffffccccdddddddd"), 1) }
        expect(NO_MATCH) { qualifierMatcher.isMatch(initByteArrayByHex("00bbbb02ffffccccdddd"), 1) }
        expect(NO_MATCH) { qualifierMatcher.isMatch(initByteArrayByHex("00bbbb02ffffaaaadddddddd"), 1) }
        expect(OUT_OF_RANGE) { qualifierMatcher.isMatch(initByteArrayByHex("00cccc02ffffccccdddddddd"), 1) }
    }
}
