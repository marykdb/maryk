package maryk.core.processors.datastore.matchers

import maryk.lib.extensions.initByteArrayByHex
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IndexPartialToMatchTest {
    @Test
    fun keyPartialToMatch() {
        val toMatch = byteArrayOf(1, 3, 5)
        assertTrue { IndexPartialToMatch(1, 2, 5, toMatch).match(byteArrayOf(0, 0, 1, 3, 5)) }
        assertFalse { IndexPartialToMatch(1, 2, 5, toMatch).match(byteArrayOf(0, 0, 9, 8, 7)) }
    }

    @Test
    fun keyPartialSizeToMatch() {
        assertTrue { IndexPartialSizeToMatch(1, null, 5, 3).match(byteArrayOf(0, 0, 1, 3, 5, 3, 2, -1, -1, -1, -1, -1)) }
        assertFalse { IndexPartialSizeToMatch(1, null, 5, 3).match(byteArrayOf(0, 0, 9, 8, 7, 8, 4, 2, -1, -1, -1, -1, -1)) }
    }

    @Test
    fun keyPartialRegExToMatch() {
        assertTrue {
            IndexPartialToRegexMatch(1, 5, Regex("^(TestMarykModel)$")).match(
                initByteArrayByHex("000000546573744d6172796b4d6f64656c0E02FFFFFFFFFF")
            )
        }
    }

    @Test
    fun keyPartialToBeBigger() {
        val toBeSmaller = byteArrayOf(1, 3)
        assertTrue { IndexPartialToBeBigger(1, 2, 5, toBeSmaller, true).match(byteArrayOf(0, 0, 2, 3, 5)) }
        assertTrue { IndexPartialToBeBigger(1, 2, 5, toBeSmaller, true).match(byteArrayOf(0, 0, 1, 5, 5)) }
        assertFalse { IndexPartialToBeBigger(1, 2, 5, toBeSmaller, true).match(byteArrayOf(0, 0, 0, 4, 5)) }
        assertFalse { IndexPartialToBeBigger(1, 2, 5, toBeSmaller, true).match(byteArrayOf(0, 0, 1, 1, 5)) }

        assertTrue { IndexPartialToBeBigger(1, 2, 5, toBeSmaller, true).match(byteArrayOf(0, 0, 1, 3, 5)) }
        assertFalse { IndexPartialToBeBigger(1, 2, 5, toBeSmaller, false).match(byteArrayOf(0, 0, 1, 3, 5)) }
    }

    @Test
    fun keyPartialToBeSmaller() {
        val toBeBigger = byteArrayOf(1, 3)
        assertTrue { IndexPartialToBeSmaller(1, 2, 5, toBeBigger, true).match(byteArrayOf(0, 0, 0, 4, 5)) }
        assertTrue { IndexPartialToBeSmaller(1, 2, 5, toBeBigger, true).match(byteArrayOf(0, 0, 1, 1, 5)) }
        assertFalse { IndexPartialToBeSmaller(1, 2, 5, toBeBigger, true).match(byteArrayOf(0, 0, 2, 3, 5)) }
        assertFalse { IndexPartialToBeSmaller(1, 2, 5, toBeBigger, true).match(byteArrayOf(0, 0, 1, 5, 5)) }

        assertTrue { IndexPartialToBeSmaller(1, 2, 5, toBeBigger, true).match(byteArrayOf(0, 0, 1, 3, 5)) }
        assertFalse { IndexPartialToBeSmaller(1, 2, 5, toBeBigger, false).match(byteArrayOf(0, 0, 1, 3, 5)) }
    }

    @Test
    fun keyPartialToBeOneOf() {
        val toBeOneOf = listOf(
            byteArrayOf(0, 3),
            byteArrayOf(1, 2),
            byteArrayOf(5, 6)
        )

        assertTrue { IndexPartialToBeOneOf(1, 1, 3, toBeOneOf).match(byteArrayOf(-1, 1, 2)) }
        assertFalse { IndexPartialToBeOneOf(1, 1, 3, toBeOneOf).match(byteArrayOf(99, 3, 2)) }
        assertTrue { IndexPartialToBeOneOf(1, 1, 4, toBeOneOf).match(byteArrayOf(5, 5, 6, 4)) }
    }
}
