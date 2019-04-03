package maryk.core.processors.datastore.matchers

import maryk.core.processors.datastore.matchers.FuzzyMatchResult.MATCH
import maryk.core.processors.datastore.matchers.FuzzyMatchResult.NO_MATCH
import maryk.core.processors.datastore.matchers.FuzzyMatchResult.OUT_OF_RANGE
import maryk.lib.extensions.initByteArrayByHex
import maryk.lib.extensions.toHex
import maryk.test.shouldBe
import kotlin.test.Test

class QualifierMatcherTest {
    @Test
    fun exactMatch() {
        val qualifierMatcher = QualifierExactMatcher(
            initByteArrayByHex("BBBB")
        )
        qualifierMatcher.compareTo(initByteArrayByHex("BBBB"), 0) shouldBe 0
        qualifierMatcher.compareTo(initByteArrayByHex("0000BBBB"), 2) shouldBe 0
        qualifierMatcher.compareTo(initByteArrayByHex("0000AAAA"), 2) shouldBe 17
        qualifierMatcher.compareTo(initByteArrayByHex("0000CCCC"), 2) shouldBe -17
    }

    @Test
    fun fuzzyMatch() {
        val qualifierMatcher = QualifierFuzzyMatcher(
            arrayOf(
                initByteArrayByHex("bbbb"),
                initByteArrayByHex("cccc")
            ),
            arrayOf(
                FuzzyDynamicLengthMatch,
                FuzzyExactLengthMatch(4)
            )
        )
        qualifierMatcher.firstPossible().toHex() shouldBe "bbbb"
        qualifierMatcher.isMatch(initByteArrayByHex("00bbbb02ffffccccdddddddd"), 1) shouldBe MATCH
        qualifierMatcher.isMatch(initByteArrayByHex("00bbbb02ffffccccdddd"), 1) shouldBe NO_MATCH
        qualifierMatcher.isMatch(initByteArrayByHex("00bbbb02ffffaaaadddddddd"), 1) shouldBe NO_MATCH
        qualifierMatcher.isMatch(initByteArrayByHex("00cccc02ffffccccdddddddd"), 1) shouldBe OUT_OF_RANGE
    }
}
