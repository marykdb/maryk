package maryk.core.processors.datastore.matchers

import maryk.core.processors.datastore.matchers.FuzzyMatchResult.MATCH
import maryk.core.processors.datastore.matchers.FuzzyMatchResult.NO_MATCH
import maryk.core.processors.datastore.matchers.FuzzyMatchResult.OUT_OF_RANGE
import maryk.lib.exceptions.ParseException
import kotlin.test.Test
import kotlin.test.assertFailsWith
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

    @Test
    fun fuzzyMatchRejectsInvalidRange() {
        val qualifierMatcher = QualifierFuzzyMatcher(
            reference = null,
            listOf("bbbb".hexToByteArray()),
            emptyList()
        )

        expect(NO_MATCH) { qualifierMatcher.isMatch("bbbb".hexToByteArray(), -1, 1) }
        expect(NO_MATCH) { qualifierMatcher.isMatch("bbbb".hexToByteArray(), 0, -1) }
        expect(NO_MATCH) { qualifierMatcher.isMatch("bbbb".hexToByteArray(), 5, 1) }
    }

    @Test
    fun fuzzyMatchHandlesOverflowingLengthAsClampedRange() {
        val qualifierMatcher = QualifierFuzzyMatcher(
            reference = null,
            listOf("bbbb".hexToByteArray()),
            emptyList()
        )

        expect(MATCH) { qualifierMatcher.isMatch("bbbb".hexToByteArray(), 0, Int.MAX_VALUE) }
    }

    @Test
    fun dynamicFuzzyMatchRejectsNegativeLength() {
        val bytes = byteArrayOf(-1, -1, -1, -1, 15)
        var index = 0

        assertFailsWith<ParseException> {
            FuzzyDynamicLengthMatch.skip { bytes[index++] }
        }
    }
}
