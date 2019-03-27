package maryk.core.processors.datastore

import maryk.test.shouldBe
import kotlin.test.Test

class IndexPartialToMatchTest {
    @Test
    fun keyPartialToMatch() {
        val toMatch = byteArrayOf(1, 3, 5)
        IndexPartialToMatch(1, 2, 5, toMatch).match(byteArrayOf(0, 0, 1, 3, 5)) shouldBe true
        IndexPartialToMatch(1, 2, 5, toMatch).match(byteArrayOf(0, 0, 9, 8, 7)) shouldBe false
    }

    @Test
    fun keyPartialSizeToMatch() {
        IndexPartialSizeToMatch(1, null, 5, 3).match(byteArrayOf(0, 0, 1, 3, 5, 3, 2, -1, -1, -1, -1, -1)) shouldBe true
        IndexPartialSizeToMatch(1, null, 5, 3).match(byteArrayOf(0, 0, 9, 8, 7, 8, 4, 2, -1, -1, -1, -1, -1)) shouldBe false
    }

    @Test
    fun keyPartialToBeBigger() {
        val toBeSmaller = byteArrayOf(1, 3)
        IndexPartialToBeBigger(1, 2, 5, toBeSmaller, true).match(byteArrayOf(0, 0, 2, 3, 5)) shouldBe true
        IndexPartialToBeBigger(1, 2, 5, toBeSmaller, true).match(byteArrayOf(0, 0, 1, 5, 5)) shouldBe true
        IndexPartialToBeBigger(1, 2, 5, toBeSmaller, true).match(byteArrayOf(0, 0, 0, 4, 5)) shouldBe false
        IndexPartialToBeBigger(1, 2, 5, toBeSmaller, true).match(byteArrayOf(0, 0, 1, 1, 5)) shouldBe false

        IndexPartialToBeBigger(1, 2, 5, toBeSmaller, true).match(byteArrayOf(0, 0, 1, 3, 5)) shouldBe true
        IndexPartialToBeBigger(1, 2, 5, toBeSmaller, false).match(byteArrayOf(0, 0, 1, 3, 5)) shouldBe false
    }

    @Test
    fun keyPartialToBeSmaller() {
        val toBeBigger = byteArrayOf(1, 3)
        IndexPartialToBeSmaller(1, 2, 5, toBeBigger, true).match(byteArrayOf(0, 0, 0, 4, 5)) shouldBe true
        IndexPartialToBeSmaller(1, 2, 5, toBeBigger, true).match(byteArrayOf(0, 0, 1, 1, 5)) shouldBe true
        IndexPartialToBeSmaller(1, 2, 5, toBeBigger, true).match(byteArrayOf(0, 0, 2, 3, 5)) shouldBe false
        IndexPartialToBeSmaller(1, 2, 5, toBeBigger, true).match(byteArrayOf(0, 0, 1, 5, 5)) shouldBe false

        IndexPartialToBeSmaller(1, 2, 5, toBeBigger, true).match(byteArrayOf(0, 0, 1, 3, 5)) shouldBe true
        IndexPartialToBeSmaller(1, 2, 5, toBeBigger, false).match(byteArrayOf(0, 0, 1, 3, 5)) shouldBe false
    }

    @Test
    fun keyPartialToBeOneOf() {
        val toBeOneOf = listOf(
            byteArrayOf(0, 3),
            byteArrayOf(1, 2),
            byteArrayOf(5, 6)
        )

        IndexPartialToBeOneOf(1, 1, 3, toBeOneOf).match(byteArrayOf(-1, 1, 2)) shouldBe true
        IndexPartialToBeOneOf(1, 1, 3, toBeOneOf).match(byteArrayOf(99, 3, 2)) shouldBe false
        IndexPartialToBeOneOf(1, 1, 4, toBeOneOf).match(byteArrayOf(5, 5, 6, 4)) shouldBe true
    }
}
