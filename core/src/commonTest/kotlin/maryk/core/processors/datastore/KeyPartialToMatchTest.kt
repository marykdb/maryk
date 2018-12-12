package maryk.core.processors.datastore

import maryk.test.shouldBe
import kotlin.test.Test

class KeyPartialToMatchTest {
    @Test
    fun keyPartialToMatch() {
        val toMatch = byteArrayOf(1, 3, 5)
        KeyPartialToMatch(2, toMatch).match(byteArrayOf(0, 0, 1, 3, 5)) shouldBe true
        KeyPartialToMatch(2, toMatch).match(byteArrayOf(0, 0, 9, 8, 7)) shouldBe false
    }

    @Test
    fun keyPartialToBeBigger() {
        val toBeSmaller = byteArrayOf(1, 3)
        KeyPartialToBeBigger(2, toBeSmaller, true).match(byteArrayOf(0, 0, 2, 3, 5)) shouldBe true
        KeyPartialToBeBigger(2, toBeSmaller, true).match(byteArrayOf(0, 0, 1, 5, 5)) shouldBe true
        KeyPartialToBeBigger(2, toBeSmaller, true).match(byteArrayOf(0, 0, 0, 4, 5)) shouldBe false
        KeyPartialToBeBigger(2, toBeSmaller, true).match(byteArrayOf(0, 0, 1, 1, 5)) shouldBe false

        KeyPartialToBeBigger(2, toBeSmaller, true).match(byteArrayOf(0, 0, 1, 3, 5)) shouldBe true
        KeyPartialToBeBigger(2, toBeSmaller, false).match(byteArrayOf(0, 0, 1, 3, 5)) shouldBe false
    }

    @Test
    fun keyPartialToBeSmaller() {
        val toBeBigger = byteArrayOf(1, 3)
        KeyPartialToBeSmaller(2, toBeBigger, true).match(byteArrayOf(0, 0, 0, 4, 5)) shouldBe true
        KeyPartialToBeSmaller(2, toBeBigger, true).match(byteArrayOf(0, 0, 1, 1, 5)) shouldBe true
        KeyPartialToBeSmaller(2, toBeBigger, true).match(byteArrayOf(0, 0, 2, 3, 5)) shouldBe false
        KeyPartialToBeSmaller(2, toBeBigger, true).match(byteArrayOf(0, 0, 1, 5, 5)) shouldBe false

        KeyPartialToBeSmaller(2, toBeBigger, true).match(byteArrayOf(0, 0, 1, 3, 5)) shouldBe true
        KeyPartialToBeSmaller(2, toBeBigger, false).match(byteArrayOf(0, 0, 1, 3, 5)) shouldBe false
    }

    @Test
    fun keyPartialToBeOneOf() {
        val toBeOneOf = listOf(
            byteArrayOf(0, 3),
            byteArrayOf(1, 2),
            byteArrayOf(5, 6)
        )

        KeyPartialToBeOneOf(1, toBeOneOf).match(byteArrayOf(-1, 1, 2)) shouldBe true
        KeyPartialToBeOneOf(1, toBeOneOf).match(byteArrayOf(99, 3, 2)) shouldBe false
        KeyPartialToBeOneOf(1, toBeOneOf).match(byteArrayOf(5, 5, 6, 4)) shouldBe true
    }
}
